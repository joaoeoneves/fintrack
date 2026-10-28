package com.joaoeoneves.fintrack.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecPlannedIncomeTest {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""

    @Test
    fun v1File_decodesWithEmptyPlanAndCurrentSchemaVersion() {
        val income = """[{"id":"i1","source":"Pay","amountCents":5,"date":"2026-03-01T08:00:00Z"}]"""
        val text = """{$header,"schemaVersion":1,"income":$income}"""

        val result = BackupCodec.decode(text).success()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertTrue(result.document.plannedIncome.isEmpty())
        assertEquals(1, result.document.income.size)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v2RoundTrip_keepsPlannedIncome() {
        val doc = backupDoc().copy(plannedIncome = listOf(backupPlanned("p1"), backupPlanned("p2", "Rent", 5, 1)))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(2, result.document.counts().plannedIncome)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v16File_isUnsupported() {
        val text = """{$header,"schemaVersion":17}"""

        assertEquals(BackupDecodeResult.UnsupportedVersion(17), BackupCodec.decode(text))
    }

    @Test
    fun invalidPlannedIncome_isDroppedAndCounted() {
        val doc =
            backupDoc().copy(
                plannedIncome =
                    listOf(
                        backupPlanned("ok"),
                        backupPlanned("zero", amountCents = 0),
                        backupPlanned("neg", amountCents = -1),
                        backupPlanned("blank", label = "  "),
                        backupPlanned("long", label = "x".repeat(200)),
                        backupPlanned(" "),
                    ),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf("ok"), result.document.plannedIncome.map { it.id })
        assertEquals(5, result.skippedCount)
    }

    @Test
    fun duplicatePlannedIncomeIds_keepFirstAndCountRest() {
        val doc = backupDoc().copy(plannedIncome = listOf(backupPlanned("p1", "A"), backupPlanned("p1", "B")))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf("A"), result.document.plannedIncome.map { it.label })
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun countsTotal_includesPlannedIncome() {
        assertEquals(15, BackupCounts(1, 2, 3, 4, 5, 0).total)
        assertEquals(1, backupDoc().copy(plannedIncome = listOf(backupPlanned())).counts().total)
    }

    @Test
    fun earmarkedLines_roundTrip() {
        val lines =
            listOf(
                backupPlanned("p1").copy(earmarkType = "GROUP", earmarkId = "g1"),
                backupPlanned("p2").copy(earmarkType = "CATEGORY", earmarkId = "FOOD"),
                backupPlanned("p3"),
            )
        val doc = backupDoc().copy(plannedIncome = lines)

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(lines, result.document.plannedIncome)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun v6File_decodesUpgradedToV14_withoutEarmarks() {
        val planned = """[{"id":"p","label":"Pay","amountCents":50000,"order":0,"frequency":"MONTHLY"}]"""
        val text = """{$header,"schemaVersion":6,"plannedIncome":$planned}"""

        val result = BackupCodec.decode(text).success()

        assertEquals(16, result.document.schemaVersion)
        assertEquals(1, result.document.plannedIncome.size)
        assertEquals(
            null,
            result.document.plannedIncome
                .single()
                .earmarkType,
        )
        assertEquals(
            null,
            result.document.plannedIncome
                .single()
                .earmarkId,
        )
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun invalidEarmark_dropsTheLine() {
        val doc =
            backupDoc().copy(
                plannedIncome =
                    listOf(
                        backupPlanned("ok").copy(earmarkType = "GROUP", earmarkId = "g1"),
                        backupPlanned("half").copy(earmarkType = "GROUP"),
                        backupPlanned("type").copy(earmarkType = "WALLET", earmarkId = "x"),
                    ),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(listOf("ok"), result.document.plannedIncome.map { it.id })
        assertEquals(2, result.skippedCount)
    }
}
