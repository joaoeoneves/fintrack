package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalAutomation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCodecSalaryShiftTest {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""

    @Test
    fun v17File_decodesWithNullShift() {
        val text = """{$header,"schemaVersion":17,"automation":{"paydayEnabled":true}}"""

        val automation = BackupCodec.decode(text).success().let { it.document.automation }

        assertNull(automation?.salaryPaysNextMonth)
        assertEquals(true, automation?.paydayEnabled)
    }

    @Test
    fun v18_roundTripsTrueAndFalse() {
        for (value in listOf(true, false)) {
            val doc = backupDoc().copy(automation = LocalAutomation(salaryPaysNextMonth = value))

            val result = BackupCodec.decode(BackupCodec.encode(doc)).success().document

            assertEquals(value, result.automation?.salaryPaysNextMonth)
        }
    }
}
