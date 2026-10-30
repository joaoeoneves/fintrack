package com.joaoeoneves.fintrack.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.SignInCancelledException
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.LanguageRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
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
        private val sessionRepository: SessionRepository,
        // Nullable with a default so existing unit tests that construct this ViewModel directly
        // (bypassing Hilt) with only the original 3 positional args keep compiling; Hilt itself
        // always supplies it in production. When null (test-only), the language always reads as
        // AppLanguage.SYSTEM, matching SettingsUiState()'s own default.
        private val languageRepository: LanguageRepository? = null,
    ) : ViewModel() {
        private val signOutError = MutableStateFlow<UiText?>(null)
        private val signInError = MutableStateFlow<UiText?>(null)
        private val errors = combine(signOutError, signInError) { signOut, signIn -> signOut to signIn }

        val uiState: StateFlow<SettingsUiState> =
            combine(
                themeRepository.observeThemePreference(),
                languageRepository?.observeLanguage() ?: flowOf(AppLanguage.SYSTEM),
                sessionRepository.session,
                errors,
            ) { themePreference, language, session, (signOut, signIn) ->
                SettingsUiState(
                    themePreference = themePreference,
                    language = language,
                    currentUser = (session as? Session.SignedIn)?.user,
                    signOutError = signOut,
                    isGuest = session is Session.Guest,
                    signInError = signIn,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = SettingsUiState(),
            )

        fun setThemePreference(preference: ThemePreference) {
            viewModelScope.launch { themeRepository.setThemePreference(preference) }
        }

        fun setLanguage(language: AppLanguage) {
            viewModelScope.launch { languageRepository?.setLanguage(language) }
        }

        fun signOut() {
            viewModelScope.launch {
                sessionRepository.signOut().onFailure {
                    // Deliberately not e.message: raw SDK messages aren't user-facing.
                    signOutError.value = UiText.StringResource(R.string.error_sign_out_failed)
                }
            }
        }

        fun dismissSignOutError() {
            signOutError.value = null
        }

        /** Signs in from guest mode; needs an Activity [context] for the credential picker. */
        fun signInToSync(context: Context) {
            viewModelScope.launch {
                sessionRepository.signIn(context).onFailure { e ->
                    if (e !is SignInCancelledException) {
                        // Deliberately not e.message: raw SDK messages aren't user-facing.
                        signInError.value = UiText.StringResource(R.string.error_sign_in_failed)
                    }
                }
            }
        }

        fun leaveGuestMode() {
            viewModelScope.launch {
                sessionRepository.leaveGuestMode().onFailure {
                    signOutError.value = UiText.StringResource(R.string.error_sign_out_failed)
                }
            }
        }

        fun dismissSignInError() {
            signInError.value = null
        }
    }
