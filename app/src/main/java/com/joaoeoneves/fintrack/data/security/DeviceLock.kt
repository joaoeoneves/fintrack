package com.joaoeoneves.fintrack.data.security

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether the device has a screen lock (PIN, pattern, password or biometrics) the app lock can rely on. */
fun interface DeviceLockChecker {
    fun isDeviceSecure(): Boolean
}

/** [DeviceLockChecker] backed by [KeyguardManager.isDeviceSecure]. */
class KeyguardDeviceLockChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DeviceLockChecker {
        override fun isDeviceSecure(): Boolean {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            return keyguard?.isDeviceSecure == true
        }
    }

/** Monotonic milliseconds since boot (not affected by wall-clock changes), injectable for tests. */
fun interface ElapsedRealtime {
    fun nowMs(): Long
}

/** The real [ElapsedRealtime], backed by [SystemClock.elapsedRealtime]. */
internal val SystemElapsedRealtime = ElapsedRealtime { SystemClock.elapsedRealtime() }
