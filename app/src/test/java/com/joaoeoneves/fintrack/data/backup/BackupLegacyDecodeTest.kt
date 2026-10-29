package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Old backups (schema 7-15) still decode after the legacy removal: dead fields ignored, income items skipped. */
class BackupLegacyDecodeTest {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""

    @Test
    fun v12File_withEarmarkPaydaySwitchAndIncomeRecurring_decodes_countingTheIncomeItemSkipped() {
        val text =
            """{$header,"schemaVersion":12,
            "plannedIncome":[{"id":"meal","label":"Meal","amountCents":100,"order":0,
            "earmarkType":"CATEGORY","earmarkId":"FOOD"}],
            "recurring":[
              {"id":"rent","type":"EXPENSE","name":"Rent","amountCents":5000,"categoryId":"FOOD",
               "frequency":"MONTHLY","dayOfMonth":15,"startDate":"2026-01-01"},
              {"id":"pay","type":"INCOME","name":"Pay","amountCents":9000,
               "frequency":"MONTHLY","dayOfMonth":1,"startDate":"2026-01-01"}],
            "automation":{"paydayEnabled":true,"paydayThrough":"2026-09"}}"""

        val result = BackupCodec.decode(text).success()

        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
        assertEquals(listOf("meal"), result.document.plannedIncome.map { it.id })
        assertEquals(listOf("rent"), result.document.recurring.map { it.id })
        assertEquals(1, result.skippedCount)
        assertTrue(
            result.document.automation
                ?.lineThrough
                .orEmpty()
                .isEmpty(),
        )
    }

    @Test
    fun v9File_withLegacySalaryExtras_convertsThemToExtras() {
        val text =
            """{$header,"schemaVersion":9,
            "salaryProfile":{"baseGrossCents":100000,"flexPpm":70000,"flexFirstMonth":2,
            "profitShareMultiplierPpm":500000,"profitShareMonths":[4],"bonusCents":10000,"bonusMonth":5}}"""

        val result = BackupCodec.decode(text).success()

        val profile = result.document.salaryProfile
        assertNotNull(profile)
        val extras = profile!!.toDomain().extras
        assertEquals(listOf("flex", "profitshare", "bonus"), extras.map { it.id })
        assertEquals(setOf(2, 5, 8, 11), extras.first().months)
        assertEquals(0, result.skippedCount)
    }
}
