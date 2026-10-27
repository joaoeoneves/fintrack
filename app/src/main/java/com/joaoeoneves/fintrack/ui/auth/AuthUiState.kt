package com.joaoeoneves.fintrack.ui.auth

import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.ui.common.UiText

sealed interface AuthUiState {
    /** The session hasn't been resolved yet; show nothing (avoids flashing the sign-in screen). */
    data object Starting : AuthUiState

    data object Guest : AuthUiState

    data object Idle : AuthUiState

    data object Loading : AuthUiState

    data class SignedIn(
        val user: AuthUser,
    ) : AuthUiState

    data class Error(
        val message: UiText,
    ) : AuthUiState
}
