package com.joaoeoneves.fintrack.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.initialPaydayCursor
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "AutomationSettingsVM"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * State of Settings' Automation section: whether the payday automation is on and whether an active recurring
 * income item exists (which would record the same pay twice).
 */
data class AutomationUiState(
    val paydayEnabled: Boolean = false,
    val hasRecurringIncome: Boolean = false,
)

@HiltViewModel
class AutomationSettingsViewModel
    @Inject
    constructor(
        private val repository: AutomationSettingsRepository,
        recurringRepository: RecurringRepository,
        private val clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<AutomationUiState> =
            combine(repository.observeAutomationSettings(), recurringRepository.observeRecurring()) { settings, items ->
                AutomationUiState(
                    paydayEnabled = settings.paydayEnabled,
                    hasRecurringIncome = items.any { it.active && it.type == RecurringType.INCOME },
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = AutomationUiState(),
            )

        /** Turning it on starts from the current month's payday (or last month's if it has not come yet). */
        fun onPaydayToggled(enabled: Boolean) {
            viewModelScope.launch {
                val cursor = if (enabled) initialPaydayCursor(LocalDate.now(clock)) else null
                repository.setPaydayEnabled(enabled, cursor).onFailure { Log.w(TAG, "Failed to save the setting", it) }
            }
        }
    }
