package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalAmountVersion
import com.joaoeoneves.fintrack.data.local.LocalBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        assertEquals(19, doc.schemaVersion)
        assertEquals("2026-02", doc.budgets.single().startMonth)
        assertEquals("2026-02", doc.plannedIncome.single().startMonth)
    }

    @Test
    fun v19Backup_keepsItsStartMonthsAndVersions() {
        val versions = listOf(LocalAmountVersion("2026-03", 400), LocalAmountVersion("2026-07", 500))
        val budget = LocalBudget("FOOD", 500, startMonth = "2026-03", versions = versions)
        val doc = backupDoc(budgets = listOf(budget), expenses = listOf(backupExpense(date = "2025-01-10T12:00:00Z")))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success().document

        assertEquals(19, result.schemaVersion)
        assertEquals(budget, result.budgets.single())
        assertTrue(
            result.budgets
                .single()
                .versions.size == 2,
        )
    }

    @Test
    fun v19Backup_withoutStartMonth_staysUnpinned() {
        val doc = backupDoc(budgets = listOf(LocalBudget("FOOD", 500)))

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success().document

        assertEquals(null, result.budgets.single().startMonth)
    }
}
