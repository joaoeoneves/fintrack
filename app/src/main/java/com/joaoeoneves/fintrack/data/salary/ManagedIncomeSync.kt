package com.joaoeoneves.fintrack.data.salary

import android.util.Log
import com.joaoeoneves.fintrack.data.backup.RestoreGate
import com.joaoeoneves.fintrack.data.backup.gatedBy
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.ManagedLinesSync
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.isManaged
import com.joaoeoneves.fintrack.domain.model.managedLinesSync
import com.joaoeoneves.fintrack.domain.model.relabelPaydayIncomes
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryLineLabelsProvider
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ManagedIncomeSync"

/**
 * Keeps the managed income lines in line with the stored salary profile without the user re-saving it, e.g. after
 * the way lines are generated changed or to clean up a legacy duplicate. It only repairs: it never creates the
 * first managed lines, and an identical set of writes is attempted once per process, so a failing write is not
 * retried in a loop.
 */
@Singleton
class ManagedIncomeSync
    @Inject
    constructor(
        private val profiles: SalaryProfileRepository,
        private val plannedIncome: PlannedIncomeRepository,
        private val incomes: IncomeRepository,
        private val labelsProvider: SalaryLineLabelsProvider,
        private val gate: RestoreGate,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val started = AtomicBoolean(false)
        private var lastAttempted: ManagedLinesSync? = null
        private var lastIncomeRepair: Set<String>? = null
        private var lastGeneration = 0

        fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                combine(profiles.observeSalaryProfile(), plannedIncome.observePlannedIncome(), ::Pair)
                    .catch { Log.w(TAG, "Failed to observe the salary profile", it) }
                    .gatedBy(gate)
                    .collectLatest { gated ->
                        // Paused while a backup is restored (a new emission cancels any pass in flight).
                        if (gated.restoring) return@collectLatest
                        if (gated.generation != lastGeneration) {
                            lastGeneration = gated.generation
                            lastAttempted = null
                            lastIncomeRepair = null
                        }
                        val (profile, existing) = gated.value
                        repairPaydayIncomes(existing)
                        if (profile == null || existing.none { it.isManaged }) return@collectLatest
                        val labels = labelsProvider.current()
                        val sync = managedLinesSync(profile, existing, labels, labelsProvider.knownDefaults())
                        if (sync.isEmpty || sync == lastAttempted) return@collectLatest
                        lastAttempted = sync
                        apply(sync)
                    }
            }
        }

        // Renames the incomes recorded from a generated line under another language's default name, once per
        // set of lines (so again after the session switches between guest and account).
        private suspend fun repairPaydayIncomes(existing: List<PlannedIncome>) {
            val ids = existing.filter { it.isManaged }.mapTo(mutableSetOf()) { it.id }
            if (ids.isEmpty() || ids == lastIncomeRepair) return
            lastIncomeRepair = ids
            runCatchingCancellable {
                val all = incomes.getAllIncome().getOrThrow()
                relabelPaydayIncomes(all, labelsProvider.current(), labelsProvider.knownDefaults())
                    .forEach { incomes.updateIncome(it).getOrThrow() }
            }.onFailure { e ->
                Log.w(TAG, "Failed to rename the recorded payday incomes", e)
            }
        }

        // Stops at the first failure: the next change of the data computes a fresh sync.
        private suspend fun apply(sync: ManagedLinesSync) {
            runCatchingCancellable {
                sync.upserts.forEach { plannedIncome.savePlannedIncome(it).getOrThrow() }
                sync.deletes.forEach { plannedIncome.deletePlannedIncome(it).getOrThrow() }
            }.onFailure { e ->
                Log.w(TAG, "Failed to re-sync the managed income lines", e)
            }
        }
    }
