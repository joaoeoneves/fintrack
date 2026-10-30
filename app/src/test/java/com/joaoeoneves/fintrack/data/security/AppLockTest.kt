package com.joaoeoneves.fintrack.data.security

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockTest {
    private fun lock(
        enabled: Boolean = true,
        secure: Boolean = true,
        at: Long? = 1_000L,
        now: Long = 1_000L + LOCK_TIMEOUT_MS,
    ) = shouldLockOnForeground(enabled, secure, at, now)

    @Test
    fun timeoutIsSixtySeconds() = assertEquals(60_000L, LOCK_TIMEOUT_MS)

    @Test
    fun disabled_neverLocks() = assertEquals(false, lock(enabled = false, at = null))

    @Test
    fun notSecure_neverLocks() = assertEquals(false, lock(secure = false, at = null))

    @Test
    fun noTimestamp_locks() = assertEquals(true, lock(at = null))

    @Test
    fun justUnderTimeout_doesNotLock() = assertEquals(false, lock(now = 1_000L + 59_999L))

    @Test
    fun exactlyTimeout_locks() = assertEquals(true, lock(now = 1_000L + 60_000L))

    @Test
    fun overTimeout_locks() = assertEquals(true, lock(now = 1_000L + 600_000L))
}
