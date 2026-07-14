package ptech.joaoe.agenticusage.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ptech.joaoe.agenticusage.domain.repository.AuthRepository

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
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
                onFailure = { e -> _uiState.value = AuthUiState.Error(e.message ?: "Sign-in failed") }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val result = authRepository.signOut()
            result.onFailure { e ->
                _uiState.value = AuthUiState.Error(e.message ?: "Sign-out failed")
            }
        }
    }
}
