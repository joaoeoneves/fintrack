package com.joaoeoneves.fintrack.data.migration

import android.util.Log
import com.joaoeoneves.fintrack.data.backup.RestoreGate
import com.joaoeoneves.fintrack.data.backup.gatedBy
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.IncomeMigration
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.incomeMigrationOf
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import com.joaoeoneves.fintrack.domain.usecase.PlanDataSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IncomeMigrationSync"
private const val SETTLE_DELAY_MS = 2_000L

/**
 * Folds recurring income and the old single payday switch into planned income lines without the user doing anything
 * (see [incomeMigrationOf]). It runs while there is something to migrate, so it is a no-op once migrated, an
 * identical set of writes is attempted once per process so a failing write is not retried in a loop, and writes
 * wait for the data to settle for a couple of seconds within one stable session.
 */
@Singleton
class IncomeMigrationSync
    @Inject
    constructor(
        private val sources: PlanDataSources,
        private val automation: AutomationSettingsRepository,
        private val sessions: SessionRepository,
        private val clock: Clock,
        private val gate: RestoreGate,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val started = AtomicBoolean(false)
        private var lastAttempted: IncomeMigration? = null
        private var lastGeneration = 0

        fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                combine(
                    sources.recurring.observeRecurring(),
                    sources.plannedIncome.observePlannedIncome(),
                    automation.observeAutomationSettings(),
                    ::IncomeSnapshot,
                ).catch { Log.w(TAG, "Failed to observe the income", it) }
                    .gatedBy(gate)
                    .collectLatest { gated ->
                        // Paused while a backup is restored (a new emission cancels any pass in flight).
                        if (gated.restoring) return@collectLatest
                        if (gated.generation != lastGeneration) {
                            lastGeneration = gated.generation
                            lastAttempted = null
                        }
                        val snapshot = gated.value
                        // Let a partial cache (e.g. right after sign-in) fill in, and never act mid session switch.
                        val session = sessions.session.value
                        delay(SETTLE_DELAY_MS)
                        val settled = session is Session.Guest || session is Session.SignedIn
                        if (!settled || session != sessions.session.value) return@collectLatest
                        val today = LocalDate.now(clock)
                        val migration = incomeMigrationOf(snapshot.recurring, snapshot.lines, snapshot.settings, today)
                        if (migration.isEmpty || migration == lastAttempted) return@collectLatest
                        lastAttempted = migration
                        apply(migration, snapshot.lines)
                    }
            }
        }

        // Cursors first (a line without one would only get an initial cursor), then the lines, the legacy switch off
        // and finally the recurring items; stops at the first failure.
        private suspend fun apply(
            migration: IncomeMigration,
            lines: List<PlannedIncome>,
        ) {
            runCatchingCancellable {
                if (migration.cursors.isNotEmpty()) automation.updateLineCursors(migration.cursors).getOrThrow()
                migration.newLines.forEach { sources.plannedIncome.savePlannedIncome(it).getOrThrow() }
                lines
                    .filter { it.id in migration.autoRecordLineIds }
                    .forEach { sources.plannedIncome.savePlannedIncome(it.copy(autoRecord = true)).getOrThrow() }
                if (migration.clearLegacyPayday) automation.updateLineCursors(clearLegacyPayday = true).getOrThrow()
                migration.deleteRecurringIds.forEach { sources.recurring.deleteRecurring(it).getOrThrow() }
            }.onFailure { e ->
                Log.w(TAG, "Failed to fold recurring income into planned income", e)
            }
        }
    }

private class IncomeSnapshot(
    val recurring: List<RecurringItem>,
    val lines: List<PlannedIncome>,
    val settings: AutomationSettings,
)
