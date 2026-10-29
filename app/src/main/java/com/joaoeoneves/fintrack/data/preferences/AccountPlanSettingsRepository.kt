package com.joaoeoneves.fintrack.data.preferences

import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlanSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The plan settings, kept with the account (or the guest data) through [AutomationSettingsRepository] so they follow
 * the user across devices. Until it is moved, the old device-local DataStore value is read as a fallback: the first
 * time it is seen with nothing stored for the account it is written there, and once the account value is observed
 * the DataStore value is cleared and no longer read.
 */
@Singleton
class AccountPlanSettingsRepository
    @Inject
    constructor(
        private val automation: AutomationSettingsRepository,
        private val store: UserPreferencesStore,
    ) : PlanSettingsRepository {
        override fun observeSalaryPaysNextMonth(): Flow<Boolean?> =
            combine(
                automation.observeAutomationSettings().map { it.salaryPaysNextMonth },
                store.data.map { it.salaryPaysNextMonth },
            ) { account, legacy -> account to legacy }
                .onEach { (account, legacy) ->
                    when {
                        legacy == null -> Unit
                        account == null -> automation.setSalaryPaysNextMonth(legacy)
                        else -> store.update { it.copy(salaryPaysNextMonth = null) }
                    }
                }.map { (account, legacy) -> account ?: legacy }
                .distinctUntilChanged()

        override suspend fun setSalaryPaysNextMonth(enabled: Boolean) {
            automation.setSalaryPaysNextMonth(enabled)
            // The account value now wins; drop the device-local one so it is never read again.
            store.update { it.copy(salaryPaysNextMonth = null) }
        }
    }
