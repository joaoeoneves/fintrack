package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalMonthSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Backup schema v4/v6: month settings and the income-line basis/frequency fields. */
class BackupCodecMonthSettingsTest {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""
    private val oldLine = """{"id":"p","label":"Pay","amountCents":5,"order":0}"""

    @Test
    fun v3File_decodesToV14_withEmptyMonthSettings_andDefaultedLines() {
        val result = BackupCodec.decode("""{$header,"schemaVersion":3,"plannedIncome":[$oldLine]}""").success()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertTrue(result.document.monthSettings.isEmpty())
        val line = result.document.plannedIncome.single()
        assertEquals("FIXED", line.basis)
        assertEquals("MONTHLY", line.frequency)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v1AndV2Files_stillDecode() {
        listOf(1, 2).forEach { v ->
            val result = BackupCodec.decode("""{$header,"schemaVersion":$v}""").success()
            assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        }
    }

    @Test
    fun v4RoundTrip_keepsMonthSettingsAndNewLineFields() {
        val line = backupPlanned("p1").copy(basis = "PER_WORKING_DAY", skipMonths = listOf(8))
        val yearly = backupPlanned("p2", "Bonus", 5, 1).copy(frequency = "YEARLY", monthOfYear = 7)
        val doc =
            backupDoc().copy(
                plannedIncome = listOf(line, yearly),
                monthSettings = listOf(LocalMonthSettings("2026-10", 15), LocalMonthSettings("2026-11", 2)),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(2, result.document.counts().monthSettings)
        assertEquals(4, result.document.counts().total)
    }

    @Test
    fun invalidMonthSettings_areDroppedAndCounted() {
        val doc =
            backupDoc().copy(
                monthSettings =
                    listOf(
                        LocalMonthSettings("2026-10", 15),
                        LocalMonthSettings("2026-11", 24),
                        LocalMonthSettings("bad", 5),
                        LocalMonthSettings("2026-12", -1),
                    ),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf(LocalMonthSettings("2026-10", 15)), result.document.monthSettings)
        assertEquals(3, result.skippedCount)
    }

    @Test
    fun duplicateMonthSettings_keepOnePerMonth() {
        val doc =
            backupDoc().copy(
                monthSettings = listOf(LocalMonthSettings("2026-10", 15), LocalMonthSettings("2026-10", 16)),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(1, result.document.monthSettings.size)
    }

    @Test
    fun inconsistentIncomeLines_areDropped() {
        val bad = backupPlanned("bad").copy(frequency = "YEARLY")
        val doc = backupDoc().copy(plannedIncome = listOf(backupPlanned("ok"), bad))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf("ok"), result.document.plannedIncome.map { it.id })
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun v5File_withOnlyTheRemovedWorkingDays_decodesWithoutThatRecord_andNothingSkipped() {
        val v5 =
            """{$header,"schemaVersion":5,"monthSettings":[{"month":"2026-10","workingDays":15}]}"""

        val result = BackupCodec.decode(v5).success()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertTrue(result.document.monthSettings.isEmpty())
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v6RoundTrip_preservesDaysOff() {
        val doc =
            backupDoc().copy(
                monthSettings =
                    listOf(
                        LocalMonthSettings("2026-10", 2),
                        LocalMonthSettings("2026-11", 3),
                    ),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc.monthSettings, result.document.monthSettings)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun invalidDaysOff_andEmptyRecords_areDroppedAndCounted() {
        val doc =
            backupDoc().copy(
                monthSettings =
                    listOf(
                        LocalMonthSettings("2026-10", 2),
                        LocalMonthSettings("2026-11", 24),
                        LocalMonthSettings("2026-12", -1),
                        LocalMonthSettings("2027-01", 0),
                    ),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf(LocalMonthSettings("2026-10", 2)), result.document.monthSettings)
        assertEquals(3, result.skippedCount)
    }
}
