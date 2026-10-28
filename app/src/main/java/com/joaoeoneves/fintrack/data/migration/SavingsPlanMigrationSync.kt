package com.joaoeoneves.fintrack.data.migration

import android.util.Log
import com.joaoeoneves.fintrack.data.backup.RestoreGate
import com.joaoeoneves.fintrack.data.backup.gatedBy
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.model.SavingsPlanMigration
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.savingsPlanMigrationOf
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
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

private const val TAG = "SavingsPlanMigrationSync"
private const val SETTLE_DELAY_MS = 2_000L

/**
 * Moves savings to savings plans without the user doing anything: every standing order into a SAVING-kind category
 * becomes a goal that owns it, and an envelope holding only SAVING-kind categories is emptied (budgets kept) and
 * deleted (see [savingsPlanMigrationOf]). It runs while there is something to migrate, so it is a no-op once
 * migrated, an identical set of writes is attempted once per process so a failing write is not retried in a loop,
 * and writes wait for the data to settle for a couple of seconds within one stable session.
 */
@Singleton
class SavingsPlanMigrationSync
    @Inject
    constructor(
        private val sources: PlanDataSources,
        private val goals: GoalRepository,
        private val sessions: SessionRepository,
        private val clock: Clock,
        private val gate: RestoreGate,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val started = AtomicBoolean(false)
        private var lastAttempted: SavingsPlanMigration? = null
        private var lastGeneration = 0

        fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                combine(
                    goals.observeGoals(),
                    sources.recurring.observeRecurring(),
                    sources.categories.observeCategories(),
                    sources.envelopes.observeEnvelopes(),
                    ::SavingsSnapshot,
                ).catch { Log.w(TAG, "Failed to observe the savings", it) }
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
                        val migration = migrationOf(snapshot)
                        if (migration.isEmpty || migration == lastAttempted) return@collectLatest
                        lastAttempted = migration
                        apply(migration, snapshot.envelopes)
                    }
            }
        }

        private fun migrationOf(snapshot: SavingsSnapshot): SavingsPlanMigration =
            savingsPlanMigrationOf(
                goals = snapshot.goals,
                recurring = snapshot.recurring,
                categories = snapshot.categories,
                today = LocalDate.now(clock),
                envelopes = snapshot.envelopes,
            )

        // Goals first, then the saving-only envelopes; stops at the first failure.
        private suspend fun apply(
            migration: SavingsPlanMigration,
            envelopes: List<Envelope>,
        ) {
            runCatchingCancellable {
                (migration.patchedGoals + migration.newGoals).forEach { goals.saveGoal(it).getOrThrow() }
                migration.savingEnvelopeIds.forEach { id ->
                    val envelope = envelopes.first { it.id == id }
                    // Unfile the categories first (a save without members keeps every budget), then delete the empty
                    // envelope: deleting one with members would drop their budgets.
                    val edit = EnvelopeEdit(id, envelope.name, null, null, emptySet(), emptyMap())
                    sources.envelopes.saveEnvelope(edit).getOrThrow()
                    sources.envelopes.deleteEnvelope(id).getOrThrow()
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to migrate savings to savings plans", e)
            }
        }
    }

private class SavingsSnapshot(
    val goals: List<SavingsGoal>,
    val recurring: List<RecurringItem>,
    val categories: List<Category>,
    val envelopes: List<Envelope>,
)
