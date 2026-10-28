package com.joaoeoneves.fintrack.data

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountScope
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryExtraDeductions
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Firestore document written for a salary profile: `extras` yes, the legacy flex / profit / bonus keys no. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreSalaryProfileMapTest {
    private val legacyKeys =
        listOf(
            "flexPpm",
            "flexAnnualCents",
            "flexFirstMonth",
            "flexWithdrawalFeePpm",
            "profitShareMultiplierPpm",
            "profitShareMonth",
            "profitShareMonths",
            "bonusCents",
            "bonusMonth",
        )

    @Test
    fun extras_areWritten_withEveryField_monthsSorted() {
        val extra =
            SalaryExtra(
                "a1",
                "Flex",
                SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM,
                70_000,
                setOf(10, 1, 4),
                SalaryExtraDeductions.IRS,
                5_000,
            )

        val map = SalaryProfile(baseGrossCents = 100_000, extras = listOf(extra)).toFirestoreMap()

        assertEquals(
            listOf(
                mapOf(
                    "id" to "a1",
                    "name" to "Flex",
                    "amountType" to "PERCENT_OF_ANNUAL_GROSS_PPM",
                    "amount" to 70_000L,
                    "months" to listOf(1, 4, 10),
                    "deductions" to "IRS",
                    "feePpm" to 5_000,
                ),
            ),
            map["extras"],
        )
    }

    @Test
    fun noLegacyKeys_areEverWritten() {
        val map =
            SalaryProfile(
                baseGrossCents = 1,
                extras = listOf(SalaryExtra("a", "B", SalaryExtraAmountType.FIXED_CENTS, 5, setOf(1))),
            ).toFirestoreMap()

        assertTrue(legacyKeys.none { map.containsKey(it) })
    }

    @Test
    fun emptyExtras_areStillWrittenAsAnEmptyList() {
        val map = SalaryProfile(baseGrossCents = 1).toFirestoreMap()

        assertEquals(emptyList<Any>(), map["extras"])
    }

    @Test
    fun unsetOptionalFields_areAbsent() {
        val map = SalaryProfile(baseGrossCents = 1).toFirestoreMap()

        assertFalse(map.containsKey("netMonthlyCents"))
        assertFalse(map.containsKey("irsSubsidiesPpm"))
        assertEquals(1L, map["baseGrossCents"])
        assertEquals("GROSS", map["inputMode"])
    }

    @Test
    fun amountScope_isWrittenOnlyForPerPayment() {
        fun extraMap(scope: SalaryExtraAmountScope): Map<*, *> {
            val extra = SalaryExtra("a", "B", SalaryExtraAmountType.FIXED_CENTS, 5, setOf(1, 2), amountScope = scope)
            val extras = SalaryProfile(baseGrossCents = 1, extras = listOf(extra)).toFirestoreMap()["extras"]
            return (extras as List<*>).single() as Map<*, *>
        }

        assertEquals("PER_PAYMENT", extraMap(SalaryExtraAmountScope.PER_PAYMENT)["amountScope"])
        assertFalse(extraMap(SalaryExtraAmountScope.TOTAL).containsKey("amountScope"))
    }
}
