package com.joaoeoneves.fintrack.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import com.joaoeoneves.fintrack.domain.repository.CurrencyRepository
import com.joaoeoneves.fintrack.domain.repository.LanguageRepository
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// How long (in ms) the underlying repository flows are kept alive after the last collector
// disappears, so a quick configuration change doesn't tear down and immediately re-establish them.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val themeRepository: ThemeRepository,
        private val currencyRepository: CurrencyRepository,
        private val authRepository: AuthRepository,
        // Nullable with defaults so existing unit tests that construct this ViewModel directly
        // (bypassing Hilt) with only the original 3 positional args keep compiling; Hilt itself
        // always supplies both in production. When languageRepository is null (test-only), the
        // language always reads as AppLanguage.PORTUGUESE, matching SettingsUiState()'s own default.
        // When context is null, the signOutError fallback below matches the exact literal those
        // tests assert on.
        private val languageRepository: LanguageRepository? = null,
        @param:ApplicationContext private val context: Context? = null,
    ) : ViewModel() {
        private val signOutError = MutableStateFlow<String?>(null)

        val uiState: StateFlow<SettingsUiState> =
            combine(
                themeRepository.observeThemePreference(),
                currencyRepository.observeCurrency(),
                languageRepository?.observeLanguage() ?: flowOf(AppLanguage.PORTUGUESE),
                authRepository.observeCurrentUser(),
                signOutError,
            ) { themePreference, currency, language, currentUser, error ->
                SettingsUiState(
                    themePreference = themePreference,
                    currency = currency,
                    language = language,
                    currentUser = currentUser,
                    signOutError = error,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = SettingsUiState(),
            )

        fun setThemePreference(preference: ThemePreference) {
            viewModelScope.launch { themeRepository.setThemePreference(preference) }
        }

        fun setCurrency(currency: CurrencyOption) {
            viewModelScope.launch { currencyRepository.setCurrency(currency) }
        }

        fun setLanguage(language: AppLanguage) {
            viewModelScope.launch { languageRepository?.setLanguage(language) }
        }

        fun signOut() {
            viewModelScope.launch {
                authRepository.signOut().onFailure { e ->
                    val fallback = context?.getString(R.string.error_sign_out_failed) ?: "Sign-out failed"
                    signOutError.value = e.message ?: fallback
                }
            }
        }

        fun dismissSignOutError() {
            signOutError.value = null
        }
    }
