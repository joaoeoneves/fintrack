package com.joaoeoneves.fintrack.data.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** TEMPORARY stub of the Track A contract. */
@Singleton
class AppLockController
    @Inject
    constructor() {
        private val _state = MutableStateFlow<LockState>(LockState.Unlocked)
        val state: StateFlow<LockState> = _state
        private val _enabled = MutableStateFlow(false)
        val enabled: StateFlow<Boolean> = _enabled

        fun onBackground() = Unit

        fun onForeground() = Unit

        fun onAuthenticationStarted() = Unit

        fun onAuthenticated() {
            _state.value = LockState.Unlocked
        }

        fun onAuthenticationEnded() = Unit

        fun onUnavailable() {
            _state.value = LockState.Unlocked
        }

        suspend fun setEnabled(enabled: Boolean) {
            _enabled.value = enabled
        }
    }
