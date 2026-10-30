package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalAmountVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCodecStartMonthTest {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""

    @Test
    fun v18Backup_isUpgradedWithTheEarliestMonthWithData() {
        val text =
            """{$header,"schemaVersion":18,
            |"expenses":[{"id":"e1","name":"x","amountCents":100,"categoryId":"FOOD","date":"2026-04-10T12:00:00Z"}],
            |"income":[{"id":"i1","source":"s","amountCents":100,"date":"2026-02-01T12:00:00Z"}],
            |"budgets":[{"categoryId":"FOOD","limitCents":500}],
            |"plannedIncome":[{"id":"p","label":"Pay","amountCents":1000,"order":0}]}
            """.trimMargin()

        val doc = BackupCodec.decode(text).success().document

        assertEquals(BACKUP_SCHEMA_VERSION, doc.schemaVersion)
        assertEquals("2026-02", doc.plannedIncome.single().startMonth)
    }

    @Test
    fun v20Backup_keepsItsIncomeLineStartMonthsAndVersions() {
        val versions = listOf(LocalAmountVersion("2026-03", 400), LocalAmountVersion("2026-07", 500))
        val line = backupPlanned("p1").copy(startMonth = "2026-03", versions = versions)
        val doc = backupDoc(expenses = listOf(backupExpense(date = "2025-01-10T12:00:00Z"))).copy(plannedIncome = listOf(line))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success().document

        assertEquals(BACKUP_SCHEMA_VERSION, result.schemaVersion)
        assertEquals(line, result.plannedIncome.single())
    }

    @Test
    fun v19Backup_withoutStartMonth_staysUnpinned() {
        val doc = backupDoc().copy(plannedIncome = listOf(backupPlanned("p1")))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success().document

        assertNull(result.plannedIncome.single().startMonth)
    }
}
