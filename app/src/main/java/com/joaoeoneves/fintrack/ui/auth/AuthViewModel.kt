package com.joaoeoneves.fintrack.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
        // Nullable with a default so existing unit tests that construct this ViewModel directly
        // (bypassing Hilt) keep compiling; Hilt itself always supplies a real ApplicationContext in
        // production. When null (test-only), the fallback strings below match the exact literals
        // those tests assert on.
        @param:ApplicationContext private val appContext: Context? = null,
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
                        val fallback = appContext?.getString(R.string.error_sign_in_failed) ?: "Sign-in failed"
                        _uiState.value = AuthUiState.Error(e.message ?: fallback)
                    },
                )
            }
        }

        fun signOut() {
            viewModelScope.launch {
                val result = authRepository.signOut()
                result.onFailure { e ->
                    val fallback = appContext?.getString(R.string.error_sign_out_failed) ?: "Sign-out failed"
                    _uiState.value = AuthUiState.Error(e.message ?: fallback)
                }
            }
        }
    }
