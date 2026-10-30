package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import com.joaoeoneves.fintrack.data.local.toDomain
import com.joaoeoneves.fintrack.domain.model.MealPayment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 5.2 fields in the backup file: round-trip, v19 files decode with defaults, bad values are dropped. */
class BackupCodecMealCardTest {
    private val header = """"format":"fintrack-backup","exportedAt":"$EXPORTED_AT""""

    @Test
    fun roundTrip_keepsEveryNewField() {
        val profile =
            LocalSalaryProfile(
                baseGrossCents = 100_000,
                sector = "PUBLIC",
                mealPayment = "WITH_SALARY",
                otherDeductionsPpm = 35_000,
            )
        val doc =
            backupDoc(
                expenses = listOf(backupExpense().copy(paidWithMealCard = true)),
                categories =
                    listOf(LocalCategory("c1", "Canteen", "home", "teal", "SPENDING", 1, mealCardByDefault = true)),
            ).copy(
                salaryProfile = profile,
                plannedIncome = listOf(backupPlanned().copy(payDay = 1, payDayEarlierIfClosed = true)),
            )

        val result = BackupCodec.decode(BackupCodec.encode(doc)).success()

        assertEquals(doc, result.document)
        assertEquals(0, result.skippedCount)
        assertEquals(BACKUP_SCHEMA_VERSION, result.document.schemaVersion)
    }

    @Test
    fun v19File_decodesWithDefaults() {
        val text =
            """{$header,"schemaVersion":19,
            "expenses":[{"id":"e1","name":"Lunch","amountCents":1000,"categoryId":"FOOD","date":"2026-03-10T12:00:00Z"}],
            "plannedIncome":[{"id":"p","label":"Pay","amountCents":50000,"order":0,"frequency":"MONTHLY","payDay":1}],
            "salaryProfile":{"baseGrossCents":100000}}"""

        val result = BackupCodec.decode(text).success()

        assertEquals(0, result.skippedCount)
        assertFalse(
            result.document.expenses
                .single()
                .paidWithMealCard,
        )
        assertFalse(
            result.document.plannedIncome
                .single()
                .payDayEarlierIfClosed,
        )
        val profile = requireNotNull(result.document.salaryProfile).toDomain()
        assertNull(profile.sector)
        assertEquals(MealPayment.CARD, profile.mealPayment)
        assertEquals(0, profile.otherDeductionsPpm)
    }

    @Test
    fun profileWithUnknownEnumOrOutOfRangeDeductions_isDroppedAndCounted() {
        val bad =
            listOf(
                LocalSalaryProfile(sector = "FREELANCE"),
                LocalSalaryProfile(mealPayment = "CASH"),
                LocalSalaryProfile(otherDeductionsPpm = 1_000_001),
                LocalSalaryProfile(otherDeductionsPpm = -1),
            )
        for (profile in bad) {
            val result = BackupCodec.decode(BackupCodec.encode(backupDoc().copy(salaryProfile = profile))).success()

            assertNull(profile.toString(), result.document.salaryProfile)
            assertEquals(1, result.skippedCount)
        }
    }

    @Test
    fun unknownKeysInAFutureShapedProfile_areIgnored() {
        val text = """{$header,"schemaVersion":20,"salaryProfile":{"baseGrossCents":1,"somethingNew":true}}"""

        assertTrue(
            BackupCodec
                .decode(text)
                .success()
                .document.salaryProfile != null,
        )
    }
}
