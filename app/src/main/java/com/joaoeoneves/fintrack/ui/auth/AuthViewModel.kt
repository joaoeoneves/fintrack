package com.joaoeoneves.fintrack.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.SignInCancelledException
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
        val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                authRepository.observeCurrentUser().collect { user ->
                    _uiState.value = if (user != null) AuthUiState.SignedIn(user) else AuthUiState.Idle
                }
            }
        }

        fun signIn(context: Context) {
            viewModelScope.launch {
                _uiState.value = AuthUiState.Loading
                val result = authRepository.signIn(context)
                result.fold(
                    onSuccess = { user -> _uiState.value = AuthUiState.SignedIn(user) },
                    onFailure = { e ->
                        _uiState.value =
                            if (e is SignInCancelledException) {
                                AuthUiState.Idle
                            } else {
                                // Deliberately not e.message: raw SDK/credential-provider messages
                                // aren't user-facing (the repository logs the details instead).
                                AuthUiState.Error(UiText.StringResource(R.string.error_sign_in_failed))
                            }
                    },
                )
            }
        }

        fun signOut() {
            viewModelScope.launch {
                val result = authRepository.signOut()
                result.onFailure {
                    // Deliberately not e.message (see signIn above).
                    _uiState.value = AuthUiState.Error(UiText.StringResource(R.string.error_sign_out_failed))
                }
            }
        }
    }
