package com.joaoeoneves.fintrack.data.security

import com.joaoeoneves.fintrack.data.preferences.AppLockStore
import com.joaoeoneves.fintrack.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app-lock [state]. Activity lifecycle events feed [onBackground]/[onForeground]; the lock screen
 * reports the authentication prompt through the `onAuthentication*` calls.
 *
 * Never locks the user out: if the device has no screen lock the state stays [LockState.Unlocked].
 */
@Singleton
class AppLockController
    @Inject
    constructor(
        private val store: AppLockStore,
        private val deviceLock: DeviceLockChecker,
        private val clock: ElapsedRealtime,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        /** The stored setting; `false` until the first read. */
        val enabled: StateFlow<Boolean> = store.enabled.stateIn(scope, SharingStarted.Eagerly, false)

        private val _state = MutableStateFlow<LockState>(LockState.Unknown)

        /** [LockState.Unknown] until the stored setting is read, then locked on cold start if enabled and secure. */
        val state: StateFlow<LockState> = _state.asStateFlow()

        private var backgroundedAtMs: Long? = null
        private var authenticating = false

        init {
            scope.launch {
                val on = store.enabled.first()
                if (_state.value == LockState.Unknown) {
                    _state.value = if (on && deviceLock.isDeviceSecure()) LockState.Locked else LockState.Unlocked
                }
            }
        }

        /** The app left the foreground (not a configuration change). */
        fun onBackground() {
            if (authenticating) return
            backgroundedAtMs = clock.nowMs()
        }

        /** The app came back; locks if the timeout elapsed. */
        fun onForeground() {
            if (authenticating) return
            val since = backgroundedAtMs
            backgroundedAtMs = null
            if (_state.value == LockState.Unknown || since == null) return
            if (shouldLockOnForeground(enabled.value, deviceLock.isDeviceSecure(), since, clock.nowMs())) {
                _state.value = LockState.Locked
            }
        }

        /** An authentication prompt is showing; lifecycle events are ignored until [onAuthenticationEnded]. */
        fun onAuthenticationStarted() {
            authenticating = true
        }

        fun onAuthenticated() {
            _state.value = LockState.Unlocked
        }

        fun onAuthenticationEnded() {
            authenticating = false
            backgroundedAtMs = null
        }

        /** Authentication cannot be offered (no credential or hardware): never lock the user out. */
        fun onUnavailable() {
            _state.value = LockState.Unlocked
        }

        /** Enabling keeps the app unlocked (the caller already authenticated); disabling unlocks. */
        suspend fun setEnabled(enabled: Boolean) {
            store.setEnabled(enabled)
            _state.value = LockState.Unlocked
        }
    }
