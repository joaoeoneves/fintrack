package com.joaoeoneves.fintrack.data.security

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** TEMPORARY stub of the Track A contract. */
fun interface DeviceLockChecker {
    fun isDeviceSecure(): Boolean
}

class KeyguardDeviceLockChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DeviceLockChecker {
        override fun isDeviceSecure(): Boolean =
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

fun interface ElapsedRealtime {
    fun nowMs(): Long
}

internal val SystemElapsedRealtime = ElapsedRealtime { SystemClock.elapsedRealtime() }
