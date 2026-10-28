package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.ManagedLinesSync
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.isManaged
import com.joaoeoneves.fintrack.domain.model.managedLinesSync
import com.joaoeoneves.fintrack.domain.model.staleManagedIds
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Saves or deletes the salary profile and keeps the plan's managed income lines in sync with it. */
class ManageSalaryProfile
    @Inject
    constructor(
        private val profiles: SalaryProfileRepository,
        private val plannedIncome: PlannedIncomeRepository,
    ) {
        /** Stores [profile], upserts the lines it produces and deletes the managed ones it no longer does. */
        suspend fun save(
            profile: SalaryProfile,
            labels: SalaryLineLabels,
        ): Result<Unit> =
            attempt {
                profiles.saveSalaryProfile(profile).getOrThrow()
                syncLines(profile, labels)
            }

        /** Deletes the profile and every managed line (the user's own lines stay). */
        suspend fun delete(): Result<Unit> =
            attempt {
                profiles.deleteSalaryProfile().getOrThrow()
                syncLines(null, SalaryLineLabels())
            }

        /**
         * Re-applies the stored profile to the plan's managed lines (e.g. after the line layout changed). Does
         * nothing without a profile or before its lines were first generated.
         */
        suspend fun resync(labels: SalaryLineLabels = SalaryLineLabels()): Result<Unit> =
            attempt {
                val profile = profiles.observeSalaryProfile().first() ?: return@attempt
                val existing = plannedIncome.observePlannedIncome().first()
                if (existing.any { it.isManaged }) applySync(managedLinesSync(profile, existing, labels))
            }

        private suspend fun syncLines(
            profile: SalaryProfile?,
            labels: SalaryLineLabels,
        ) {
            val existing = plannedIncome.observePlannedIncome().first()
            if (profile == null) {
                staleManagedIds(null, existing).forEach { plannedIncome.deletePlannedIncome(it).getOrThrow() }
            } else {
                applySync(managedLinesSync(profile, existing, labels))
            }
        }

        private suspend fun applySync(sync: ManagedLinesSync) {
            sync.upserts.forEach { plannedIncome.savePlannedIncome(it).getOrThrow() }
            sync.deletes.forEach { plannedIncome.deletePlannedIncome(it).getOrThrow() }
        }

        // Like runCatching, but a cancellation is rethrown instead of becoming a failure.
        @Suppress("TooGenericExceptionCaught") // Intentional: mirrors runCatching's catch-all contract.
        private inline fun attempt(block: () -> Unit): Result<Unit> =
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
    }
