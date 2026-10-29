package com.joaoeoneves.fintrack.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecRecurringTest {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""

    @Test
    fun v3RoundTrip_keepsRecurringIncludingNullEndAndCursor() {
        val items =
            listOf(
                backupRecurring("r1"),
                backupRecurring("r2", endDate = "2027-01-01", handledThrough = "2026-03-15"),
            )
        val doc = backupDoc().copy(recurring = items)

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(2, result.document.counts().recurring)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v1File_decodesWithEmptyRecurringAndCurrentSchemaVersion() {
        val result = BackupCodec.decode("""{$header,"schemaVersion":1}""").success()

        assertEquals(21, result.document.schemaVersion)
        assertTrue(result.document.recurring.isEmpty())
    }

    @Test
    fun v2File_keepsPlannedIncomeAndGetsEmptyRecurring() {
        val planned = """[{"id":"p1","label":"Pay","amountCents":5,"order":0}]"""

        val result = BackupCodec.decode("""{$header,"schemaVersion":2,"plannedIncome":$planned}""").success()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertEquals(1, result.document.plannedIncome.size)
        assertTrue(result.document.recurring.isEmpty())
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v21File_isUnsupported() {
        assertEquals(BackupDecodeResult.UnsupportedVersion(22), BackupCodec.decode("""{$header,"schemaVersion":22}"""))
    }

    @Test
    fun invalidRecurring_isDroppedAndCounted() {
        val doc =
            backupDoc().copy(
                recurring =
                    listOf(
                        backupRecurring("ok"),
                        backupRecurring("zero", amountCents = 0),
                        backupRecurring("neg", amountCents = -5),
                        backupRecurring("nocat", categoryId = null),
                        backupRecurring("incat", type = "INCOME", categoryId = "FOOD"),
                        backupRecurring("unknowncat", categoryId = "nope"),
                        backupRecurring("badtype", type = "OTHER"),
                        backupRecurring("backwards", endDate = "2025-01-01"),
                    ),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf("ok"), result.document.recurring.map { it.id })
        assertEquals(7, result.skippedCount)
    }

    @Test
    fun duplicateRecurringIds_keepFirstAndCountRest() {
        val dupes = listOf(backupRecurring("r1", amountCents = 1), backupRecurring("r1", amountCents = 2))
        val doc = backupDoc().copy(recurring = dupes)

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf(1L), result.document.recurring.map { it.amountCents })
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun recurringUsingFileCategory_isKept() {
        val doc =
            backupDoc(categories = listOf(backupCategory("gym")))
                .copy(recurring = listOf(backupRecurring("r1", categoryId = "gym")))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(1, result.document.recurring.size)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun countsTotal_includesRecurring() {
        assertEquals(21, BackupCounts(1, 2, 3, 4, 5, 6).total)
        assertEquals(1, backupDoc().copy(recurring = listOf(backupRecurring())).counts().total)
    }

    @Test
    fun v17RoundTrip_keepsSkippedAndPausedAt() {
        val doc =
            backupDoc().copy(
                recurring =
                    listOf(
                        backupRecurring("r1", skipped = listOf("2026-02-15", "2026-03-15"), pausedAt = "2026-03-01"),
                    ),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(21, BACKUP_SCHEMA_VERSION)
    }

    @Test
    fun v16File_decodesWithDefaultsForTheNewFields() {
        val recurring =
            """[{"id":"r1","type":"EXPENSE","name":"Rent","amountCents":5000,"categoryId":"FOOD",""" +
                """"frequency":"MONTHLY","dayOfMonth":15,"startDate":"2026-01-01"}]"""

        val result = BackupCodec.decode("""{$header,"schemaVersion":16,"recurring":$recurring}""").success()

        assertEquals(21, result.document.schemaVersion)
        assertTrue(
            result.document.recurring
                .single()
                .skipped
                .isEmpty(),
        )
        assertEquals(
            null,
            result.document.recurring
                .single()
                .pausedAt,
        )
    }
}
