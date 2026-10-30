package com.joaoeoneves.fintrack.data.backup

import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCodecNoLockTest {
    @Test
    fun exportJson_hasNoAppLockKey() {
        val text = BackupCodec.encode(backupDoc())

        assertFalse(text.contains("lock", ignoreCase = true))
    }
}
