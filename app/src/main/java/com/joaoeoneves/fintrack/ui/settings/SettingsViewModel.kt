package com.joaoeoneves.fintrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import com.joaoeoneves.fintrack.domain.repository.CurrencyRepository
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val themeRepository: ThemeRepository,
        private val currencyRepository: CurrencyRepository,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val signOutError = MutableStateFlow<String?>(null)

        val uiState: StateFlow<SettingsUiState> =
            combine(
                themeRepository.observeThemePreference(),
                currencyRepository.observeCurrency(),
                authRepository.observeCurrentUser(),
                signOutError,
            ) { themePreference, currency, currentUser, error ->
                SettingsUiState(
                    themePreference = themePreference,
                    currency = currency,
                    currentUser = currentUser,
                    signOutError = error,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

        fun setThemePreference(preference: ThemePreference) {
            viewModelScope.launch { themeRepository.setThemePreference(preference) }
        }

        fun setCurrency(currency: CurrencyOption) {
            viewModelScope.launch { currencyRepository.setCurrency(currency) }
        }

        fun signOut() {
            viewModelScope.launch {
                authRepository.signOut().onFailure { e ->
                    signOutError.value = e.message ?: "Sign-out failed"
                }
            }
        }

        fun dismissSignOutError() {
            signOutError.value = null
        }
    }
