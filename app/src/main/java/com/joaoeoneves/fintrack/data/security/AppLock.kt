package com.joaoeoneves.fintrack.data.security

/** TEMPORARY stub of the Track A contract. */
const val LOCK_TIMEOUT_MS = 60_000L

sealed interface LockState {
    data object Unknown : LockState

    data object Locked : LockState

    data object Unlocked : LockState
}

fun shouldLockOnForeground(
    enabled: Boolean,
    deviceSecure: Boolean,
    backgroundedAtMs: Long?,
    nowMs: Long,
    timeoutMs: Long = LOCK_TIMEOUT_MS,
): Boolean = enabled && deviceSecure && (backgroundedAtMs == null || nowMs - backgroundedAtMs >= timeoutMs)
