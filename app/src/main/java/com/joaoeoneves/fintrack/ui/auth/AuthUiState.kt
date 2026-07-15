package com.joaoeoneves.fintrack.ui.auth

import com.joaoeoneves.fintrack.domain.model.AuthUser

sealed interface AuthUiState {
    data object Idle : AuthUiState

    data object Loading : AuthUiState

    data class SignedIn(
        val user: AuthUser,
    ) : AuthUiState

    data class Error(
        val message: String,
    ) : AuthUiState
}
