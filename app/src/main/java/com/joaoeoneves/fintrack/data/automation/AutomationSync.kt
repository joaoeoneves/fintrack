package com.joaoeoneves.fintrack.data.automation

import android.util.Log
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import com.joaoeoneves.fintrack.domain.usecase.AutoPostRecurring
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
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "AutomationSync"

/**
 * Runs the automatic steps (for now: posting due auto-post recurring items) whenever the session, the data they
 * read or the app coming to the foreground changes, so a new day is picked up without a background job. Every
 * step only fills what is due; a failing step is logged and never crashes the app, and an identical situation
 * (same step inputs and day) is attempted once per process, so a failing write is not retried in a loop.
 */
@Singleton
class AutomationSync
    @Inject
    constructor(
        private val session: SessionRepository,
        private val recurringRepository: RecurringRepository,
        private val autoPostRecurring: AutoPostRecurring,
        private val clock: Clock,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val started = AtomicBoolean(false)
        private val foregroundTicks = MutableStateFlow(0)
        private var lastAttempted: Attempt? = null

        /** What a pass was run for: the same value means nothing new to do. */
        private data class Attempt(
            val session: Session,
            val today: LocalDate,
            val recurringKey: List<Any?>,
        )

        fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                combine(session.session, foregroundTicks, recurringRepository.observeRecurring(), ::Triple)
                    .catch { Log.w(TAG, "Failed to observe the automation inputs", it) }
                    .collectLatest { (current, _, recurring) ->
                        if (current !is Session.SignedIn && current != Session.Guest) return@collectLatest
                        val today = LocalDate.now(clock)
                        val key = recurring.map { listOf(it.id, it.handledThrough, it.active, it.autoPost, it.endDate) }
                        val attempt = Attempt(current, today, key)
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
        }

        @Suppress("TooGenericExceptionCaught") // Intentional: automation must never crash the app.
        private suspend fun step(
            name: String,
            block: suspend () -> Unit,
        ) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Automation step failed: $name", e)
            }
        }
    }
