package com.joaoeoneves.fintrack.data.security

/** How long the app may stay in the background before it locks again. */
const val LOCK_TIMEOUT_MS = 60_000L

/** Whether the app content is hidden behind the lock screen. */
sealed interface LockState {
    /** The setting has not been read yet. */
    data object Unknown : LockState

    data object Locked : LockState

    data object Unlocked : LockState
}

/**
 * Whether coming back to the foreground must lock the app: only when the lock is [enabled] and the device is
 * [deviceSecure], and either the app was never backgrounded ([backgroundedAtMs] null, i.e. cold start) or it was away
 * for at least [timeoutMs] (monotonic [nowMs] minus [backgroundedAtMs]).
 */
fun shouldLockOnForeground(
    enabled: Boolean,
    deviceSecure: Boolean,
    backgroundedAtMs: Long?,
    nowMs: Long,
    timeoutMs: Long = LOCK_TIMEOUT_MS,
): Boolean = enabled && deviceSecure && (backgroundedAtMs == null || nowMs - backgroundedAtMs >= timeoutMs)
