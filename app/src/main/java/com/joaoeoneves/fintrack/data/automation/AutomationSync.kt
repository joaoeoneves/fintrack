package com.joaoeoneves.fintrack.data.automation

import android.util.Log
import com.joaoeoneves.fintrack.data.backup.RestoreGate
import com.joaoeoneves.fintrack.data.backup.gatedBy
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.data.session.resubscribeOnError
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import com.joaoeoneves.fintrack.domain.usecase.AutoPostRecurring
import com.joaoeoneves.fintrack.domain.usecase.AutomationOutcome
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AutomationSync"
private const val BACKOFF_BASE_MS = 5_000L
private const val BACKOFF_MAX_SHIFT = 7
private const val BACKOFF_MAX_MS = 600_000L
private const val MAX_TRACKED_ATTEMPTS = 64

/** The repositories the sync observes besides the session, bundled for detekt's LongParameterList. */
class PaydayData
    @Inject
    constructor(
        val settings: AutomationSettingsRepository,
        val plannedIncome: PlannedIncomeRepository,
        val recurring: RecurringRepository,
        val gate: RestoreGate,
    )

/**
 * Runs the automatic steps (posting due auto-post recurring items and recording income on payday) whenever the
 * session, the data they read or the app coming to the foreground changes, so a new day is picked up without a
 * background job. Every step only fills what is due; a failing step is logged and never crashes the app, and an
 * identical situation (same step inputs and day) is attempted until a pass succeeds, then not again; a failed pass
 * (offline) is retried on the next foreground tick or emission, with a growing backoff for repeats.
 */
@Singleton
class AutomationSync
    @Inject
    constructor(
        private val session: SessionRepository,
        private val autoPostRecurring: AutoPostRecurring,
        private val paydayData: PaydayData,
        private val recordPayday: RecordPayday,
        private val clock: Clock,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val started = AtomicBoolean(false)
        private val foregroundTicks = MutableStateFlow(0)
        private var lastAttempted: Attempt? = null
        private var lastGeneration = 0
        private val attemptCounts = HashMap<Attempt, Int>()

        /** What a pass was run for: the same value means nothing new to do. */
        private data class Attempt(
            val session: Session,
            val today: LocalDate,
            val recurringKey: List<Any?>,
            val paydayKey: List<Any?>,
        )

        private data class Inputs(
            val session: Session,
            val recurring: List<RecurringItem>,
            val settings: AutomationSettings,
            val lines: List<PlannedIncome>,
        ) {
            fun recurringKey() =
                recurring.map {
                    listOf(it.id, it.handledThrough, it.active, it.autoPost, it.endDate, it.skipped, it.pausedAt)
                }

            fun paydayKey() =
                listOf(
                    settings.lineRecordedThrough,
                    // The whole line: anything that moves a pay date or an amount must re-run the pass.
                    lines,
                )
        }

        fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                combine(
                    session.session,
                    foregroundTicks,
                    paydayData.recurring.observeRecurring(),
                    paydayData.settings.observeAutomationSettings(),
                    paydayData.plannedIncome.observePlannedIncome(),
                ) { current, _, recurring, settings, lines -> Inputs(current, recurring, settings, lines) }
                    .resubscribeOnError(TAG)
                    .gatedBy(paydayData.gate)
                    .collectLatest { gated ->
                        // Paused while a backup is restored (a new emission cancels any pass in flight).
                        if (gated.restoring) return@collectLatest
                        if (gated.generation != lastGeneration) {
                            lastGeneration = gated.generation
                            lastAttempted = null
                            attemptCounts.clear()
                        }
                        val inputs = gated.value
                        val current = inputs.session
                        if (current !is Session.SignedIn && current != Session.Guest) return@collectLatest
                        val today = LocalDate.now(clock)
                        val attempt = Attempt(current, today, inputs.recurringKey(), inputs.paydayKey())
                        if (attempt == lastAttempted) return@collectLatest
                        backOffIfRepeated(attempt)
                        // Marked only once the pass ran to the end with every step succeeded: a pass cancelled by a
                        // newer emission (or by the backoff), or one that failed (offline, a rejected read), must not
                        // suppress the next identical one (the next foreground tick or emission retries it).
                        val succeeded = runSteps(today)
                        countAttempt(attempt)
                        if (succeeded) lastAttempted = attempt
                    }
            }
        }

        // A situation that comes back after being attempted means the server rejected the write and the cursor
        // rolled back: wait longer each time (cancelled by any new emission) instead of looping on the rejection.
        private suspend fun backOffIfRepeated(attempt: Attempt) {
            val repeats = attemptCounts[attempt] ?: 0
            if (repeats > 0) delay(minOf(BACKOFF_BASE_MS shl minOf(repeats, BACKOFF_MAX_SHIFT), BACKOFF_MAX_MS))
        }

        private fun countAttempt(attempt: Attempt) {
            // Bounded: a long-lived process sees many situations; forgetting old ones only drops their backoff.
            if (attemptCounts.size >= MAX_TRACKED_ATTEMPTS && attempt !in attemptCounts) attemptCounts.clear()
            attemptCounts[attempt] = minOf((attemptCounts[attempt] ?: 0) + 1, BACKOFF_MAX_SHIFT)
        }

        /** Re-checks what is due; called when the app returns to the foreground (the day may have changed). */
        fun onForeground() {
            foregroundTicks.value++
        }

        // Steps run in order and are independent: one failing must not stop the next. True when all succeeded.
        private suspend fun runSteps(today: LocalDate): Boolean {
            val recurring = step("auto-post recurring") { autoPostRecurring.run(today, clock.zone) }
            val payday = step("record payday") { recordPayday.runDueOutcome(today, clock.zone) }
            return recurring && payday
        }

        private suspend fun step(
            name: String,
            block: suspend () -> AutomationOutcome,
        ): Boolean =
            runCatchingCancellable {
                block()
            }.onFailure { e ->
                Log.w(TAG, "Automation step failed: $name", e)
            }.map { outcome ->
                // A step reports a failed read/write by its result, not only by throwing.
                if (outcome.failed) Log.w(TAG, "Automation step incomplete (will retry): $name")
                !outcome.failed
            }.getOrDefault(false)
    }
