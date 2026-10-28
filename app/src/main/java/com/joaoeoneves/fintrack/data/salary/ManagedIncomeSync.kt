package com.joaoeoneves.fintrack.data.salary

import android.util.Log
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.ManagedLinesSync
import com.joaoeoneves.fintrack.domain.model.isManaged
import com.joaoeoneves.fintrack.domain.model.managedLinesSync
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

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
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val started = AtomicBoolean(false)
        private var lastAttempted: ManagedLinesSync? = null

        fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                combine(profiles.observeSalaryProfile(), plannedIncome.observePlannedIncome(), ::Pair)
                    .catch { Log.w(TAG, "Failed to observe the salary profile", it) }
                    .collectLatest { (profile, existing) ->
                        if (profile == null || existing.none { it.isManaged }) return@collectLatest
                        val sync = managedLinesSync(profile, existing)
                        if (sync.isEmpty || sync == lastAttempted) return@collectLatest
                        lastAttempted = sync
                        apply(sync)
                    }
            }
        }

        // Stops at the first failure: the next change of the data computes a fresh sync.
        @Suppress("TooGenericExceptionCaught") // Intentional: a repair must never crash the app.
        private suspend fun apply(sync: ManagedLinesSync) {
            try {
                sync.upserts.forEach { plannedIncome.savePlannedIncome(it).getOrThrow() }
                sync.deletes.forEach { plannedIncome.deletePlannedIncome(it).getOrThrow() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to re-sync the managed income lines", e)
            }
        }
    }
