package com.joaoeoneves.fintrack.data.automation

import android.util.Log
import com.joaoeoneves.fintrack.data.backup.RestoreGate
import com.joaoeoneves.fintrack.data.backup.gatedBy
import com.joaoeoneves.fintrack.data.runCatchingCancellable
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
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AutomationSync"

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
 * identical situation (same step inputs and day) is attempted once per process, so a failing write is not retried
 * in a loop.
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
            fun recurringKey() = recurring.map { listOf(it.id, it.handledThrough, it.active, it.autoPost, it.endDate) }

            fun paydayKey() =
                listOf(
                    settings.lineRecordedThrough,
                    lines.map { listOf(it.id, it.amountCents, it.autoRecord, it.payDay, it.frequency, it.monthOfYear) },
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
                    .catch { Log.w(TAG, "Failed to observe the automation inputs", it) }
                    .gatedBy(paydayData.gate)
                    .collectLatest { gated ->
                        // Paused while a backup is restored (a new emission cancels any pass in flight).
                        if (gated.restoring) return@collectLatest
                        if (gated.generation != lastGeneration) {
                            lastGeneration = gated.generation
                            lastAttempted = null
                        }
                        val inputs = gated.value
                        val current = inputs.session
                        if (current !is Session.SignedIn && current != Session.Guest) return@collectLatest
                        val today = LocalDate.now(clock)
                        val attempt = Attempt(current, today, inputs.recurringKey(), inputs.paydayKey())
                        if (attempt == lastAttempted) return@collectLatest
                        lastAttempted = attempt
                        runSteps(today)
                    }
            }
        }

        /** Re-checks what is due; called when the app returns to the foreground (the day may have changed). */
        fun onForeground() {
            foregroundTicks.value++
        }

        // Steps run in order and are independent: one failing must not stop the next.
        private suspend fun runSteps(today: LocalDate) {
            step("auto-post recurring") { autoPostRecurring(today, clock.zone) }
            step("record payday") { recordPayday.runDue(today, clock.zone) }
        }

        private suspend fun step(
            name: String,
            block: suspend () -> Unit,
        ) {
            runCatchingCancellable {
                block()
            }.onFailure { e ->
                Log.w(TAG, "Automation step failed: $name", e)
            }
        }
    }
