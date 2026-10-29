package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_EXTRA_MULTIPLE_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraDeductions
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType.FIXED_CENTS as FIXED
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM as MULTIPLE
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM as PERCENT

/** The salary-profile record: validation mirroring `firestore.rules`, mapping, and the managed-key check. */
class SalaryProfileRecordsTest {
    private val valid = LocalSalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000, flexPpm = 70_000)

    private fun invalid(profile: LocalSalaryProfile) {
        assertThrows(IllegalArgumentException::class.java) { validateSalaryProfile(profile) }
    }

    @Test
    fun valid_passes() {
        validateSalaryProfile(valid)
        validateSalaryProfile(LocalSalaryProfile())
    }

    @Test
    fun oldDocument_withoutProfitShareMonths_decodesToEmpty() {
        val decoded = Json.decodeFromString<LocalSalaryProfile>("""{"profitShareMonth":4}""")

        assertTrue(decoded.profitShareMonths.isEmpty())
        // A lone legacy month has no multiplier to migrate, so there is no extra.
        assertEquals(emptyList<SalaryExtra>(), decoded.toDomain().extras)
        assertTrue(LocalSalaryProfile().mealSkipMonths.isEmpty())
    }

    @Test
    fun unknownInputMode_rejected() = invalid(valid.copy(inputMode = "WEIRD"))

    @Test
    fun spread_onlyZeroHalfOrFull() {
        listOf(0, 500_000, 1_000_000).forEach { validateSalaryProfile(valid.copy(subsidySpreadPpm = it)) }
        invalid(valid.copy(subsidySpreadPpm = 250_000))
        invalid(valid.copy(subsidySpreadPpm = -1))
    }

    @Test
    fun rates_mustBeZeroToOneHundredPercent() {
        validateSalaryProfile(valid.copy(ssPpm = 1_000_000, irsBasePpm = 0))
        invalid(valid.copy(ssPpm = 1_000_001))
        invalid(valid.copy(irsBasePpm = -1))
        invalid(valid.copy(irsSubsidiesPpm = 1_000_001))
        invalid(valid.copy(irsJovemExemptPpm = 1_000_001))
        invalid(valid.copy(flexPpm = 1_000_001))
        invalid(valid.copy(flexWithdrawalFeePpm = -5))
    }

    @Test
    fun profitShareMultiplier_atMostTenTimes() {
        validateSalaryProfile(valid.copy(profitShareMultiplierPpm = MAX_EXTRA_MULTIPLE_PPM))
        validateSalaryProfile(valid.copy(profitShareMultiplierPpm = 0))
        invalid(valid.copy(profitShareMultiplierPpm = MAX_EXTRA_MULTIPLE_PPM + 1))
        invalid(valid.copy(profitShareMultiplierPpm = -1))
    }

    @Test
    fun amounts_mustBePositiveAndCapped() {
        validateSalaryProfile(valid.copy(baseGrossCents = MAX_AMOUNT_CENTS))
        invalid(valid.copy(baseGrossCents = 0))
        invalid(valid.copy(baseGrossCents = -1))
        invalid(valid.copy(baseGrossCents = MAX_AMOUNT_CENTS + 1))
        invalid(valid.copy(netMonthlyCents = 0))
        invalid(valid.copy(mealRatePerDayCents = 0))
        invalid(valid.copy(bonusCents = -3))
        invalid(valid.copy(flexAnnualCents = MAX_AMOUNT_CENTS + 1))
    }

    @Test
    fun months_mustBeOneToTwelve() {
        validateSalaryProfile(valid.copy(holidaySubsidyMonth = 1, christmasSubsidyMonth = 12, flexFirstMonth = 12))
        invalid(valid.copy(holidaySubsidyMonth = 0))
        invalid(valid.copy(christmasSubsidyMonth = 13))
        invalid(valid.copy(flexFirstMonth = 0))
        invalid(valid.copy(profitShareMonth = 13))
        invalid(valid.copy(profitShareMonths = listOf(0)))
        invalid(valid.copy(profitShareMonths = listOf(13)))
        invalid(valid.copy(profitShareMonths = (1..13).toList()))
        validateSalaryProfile(valid.copy(profitShareMonths = (1..12).toList()))
        invalid(valid.copy(bonusMonth = 0))
    }

    @Test
    fun mealSkipMonths_rangeAndSize() {
        validateSalaryProfile(valid.copy(mealSkipMonths = emptyList()))
        validateSalaryProfile(valid.copy(mealSkipMonths = (1..12).toList()))
        invalid(valid.copy(mealSkipMonths = listOf(0)))
        invalid(valid.copy(mealSkipMonths = listOf(13)))
        invalid(valid.copy(mealSkipMonths = (1..13).toList()))
    }

    @Test
    fun domainRoundTrip_keepsEveryField() {
        val profile =
            SalaryProfile(
                inputMode = SalaryInputMode.NET,
                baseGrossCents = 1,
                subsidySpreadPpm = 1_000_000,
                irsBasePpm = 2,
                irsSubsidiesPpm = 3,
                ssPpm = 4,
                irsJovemExemptPpm = 5,
                netMonthlyCents = 6,
                netHolidayLumpCents = 7,
                netChristmasLumpCents = 8,
                mealRatePerDayCents = 9,
                mealSkipMonths = setOf(3, 1),
                holidaySubsidyMonth = 2,
                christmasSubsidyMonth = 3,
                extras =
                    listOf(
                        SalaryExtra("a1", "Flex", PERCENT, 10, setOf(4, 1), SalaryExtraDeductions.IRS, 12),
                        SalaryExtra("b2", "Prize", FIXED, 14, setOf(6)),
                        SalaryExtra("c3", "Profit", MULTIPLE, 13, setOf(7, 2), SalaryExtraDeductions.IRS_SS),
                    ),
            )

        assertEquals(profile, profile.toLocal().toDomain())
        assertEquals(listOf(1, 3), profile.toLocal().mealSkipMonths)
        assertEquals(
            listOf(1, 4),
            profile
                .toLocal()
                .extras
                .first()
                .months,
        )
        assertNull(profile.toLocal().flexPpm)
        assertNull(profile.toLocal().bonusCents)
        assertTrue(profile.toLocal().profitShareMonths.isEmpty())
    }

    @Test
    fun unknownInputMode_mapsToGross() {
        assertEquals(SalaryInputMode.GROSS, valid.copy(inputMode = "WEIRD").toDomain().inputMode)
    }

    @Test
    fun emptyJson_decodesToDomainDefaults() {
        val decoded = Json.decodeFromString<LocalSalaryProfile>("{}")

        assertEquals(SalaryProfile(), decoded.toDomain())
    }

    @Test
    fun managedKey_everyKnownKeyIsValid() {
        val keys =
            listOf(MANAGED_NET_SALARY, "profile_duodecimos", "profile_meal", "profile_holiday", "profile_christmas") +
                listOf("profile_extra_abc_1", "profile_extra_Zz9_12")
        keys.forEach { validatePlannedIncome(LocalPlannedIncome("p", "Pay", 5, 0, managedKey = it)) }
    }

    @Test
    fun managedKey_absentIsValid_unknownRejected() {
        validatePlannedIncome(LocalPlannedIncome("p", "Pay", 5, 0))
        listOf(
            "profile_other",
            "profile_bonus",
            "profile_profit_share",
            "profile_profit_share_3",
            "profile_flex_q2",
            "",
            "profile_extra_abc_0",
            "profile_extra_abc_13",
            "profile_extra__1",
            "profile_extra_a-b_1",
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) {
                validatePlannedIncome(LocalPlannedIncome("p", "Pay", 5, 0, managedKey = it))
            }
        }
    }

    @Test
    fun managedKey_mapsBothWays() {
        val local = LocalPlannedIncome("p", "Pay", 5, 0, managedKey = MANAGED_NET_SALARY)

        assertEquals(MANAGED_NET_SALARY, local.toDomain().managedKey)
        assertEquals(local, local.toDomain().toLocal())
        assertNull(LocalPlannedIncome("p", "Pay", 5, 0).toDomain().managedKey)
    }

    private val goodExtra = LocalSalaryExtra("e1", "Bonus", "FIXED_CENTS", 10_000, listOf(5))

    private fun withExtras(vararg extras: LocalSalaryExtra) = valid.copy(extras = extras.toList())

    @Test
    fun extras_validOnesPass_atTheCapOfTen() {
        validateSalaryProfile(withExtras(goodExtra))
        validateSalaryProfile(valid.copy(extras = List(10) { goodExtra.copy(id = "e$it") }))
        invalid(valid.copy(extras = List(11) { goodExtra.copy(id = "e$it") }))
    }

    @Test
    fun extras_idsMustBeUniqueAndAlphanumericUpToTwenty() {
        invalid(withExtras(goodExtra, goodExtra))
        invalid(withExtras(goodExtra.copy(id = "")))
        invalid(withExtras(goodExtra.copy(id = "a_b")))
        invalid(withExtras(goodExtra.copy(id = "x".repeat(21))))
        validateSalaryProfile(withExtras(goodExtra.copy(id = "x".repeat(20))))
    }

    @Test
    fun extras_nameBetweenOneAndFiftyCodePoints() {
        invalid(withExtras(goodExtra.copy(name = "")))
        invalid(withExtras(goodExtra.copy(name = "   ")))
        validateSalaryProfile(withExtras(goodExtra.copy(name = "x".repeat(50))))
        invalid(withExtras(goodExtra.copy(name = "x".repeat(51))))
        validateSalaryProfile(withExtras(goodExtra.copy(name = "\uD83D\uDE00".repeat(50))))
    }

    @Test
    fun extras_monthsNonEmptyDistinctOneToTwelve() {
        invalid(withExtras(goodExtra.copy(months = emptyList())))
        invalid(withExtras(goodExtra.copy(months = listOf(0))))
        invalid(withExtras(goodExtra.copy(months = listOf(13))))
        invalid(withExtras(goodExtra.copy(months = listOf(3, 3))))
        validateSalaryProfile(withExtras(goodExtra.copy(months = (1..12).toList())))
    }

    @Test
    fun extras_amountBoundsPerType() {
        val fixed = goodExtra
        val multiple = goodExtra.copy(amountType = "MULTIPLE_OF_BASE_PPM")
        val percent = goodExtra.copy(amountType = "PERCENT_OF_ANNUAL_GROSS_PPM")

        validateSalaryProfile(withExtras(fixed.copy(amount = 1)))
        validateSalaryProfile(withExtras(fixed.copy(amount = MAX_AMOUNT_CENTS)))
        invalid(withExtras(fixed.copy(amount = 0)))
        invalid(withExtras(fixed.copy(amount = MAX_AMOUNT_CENTS + 1)))
        validateSalaryProfile(withExtras(multiple.copy(amount = MAX_EXTRA_MULTIPLE_PPM.toLong())))
        invalid(withExtras(multiple.copy(amount = MAX_EXTRA_MULTIPLE_PPM + 1L)))
        invalid(withExtras(multiple.copy(amount = 0)))
        validateSalaryProfile(withExtras(percent.copy(amount = 1_000_000)))
        invalid(withExtras(percent.copy(amount = 1_000_001)))
        invalid(withExtras(percent.copy(amount = -1)))
    }

    @Test
    fun extras_unknownTypeOrDeductions_andFeeOverHundredPercent_areRejected() {
        invalid(withExtras(goodExtra.copy(amountType = "WEIRD")))
        invalid(withExtras(goodExtra.copy(deductions = "WEIRD")))
        validateSalaryProfile(withExtras(goodExtra.copy(deductions = "IRS_SS", feePpm = 1_000_000)))
        invalid(withExtras(goodExtra.copy(feePpm = 1_000_001)))
        invalid(withExtras(goodExtra.copy(feePpm = -1)))
    }

    @Test
    fun toDomain_dropsExtrasWithUnknownEnums() {
        val badType = goodExtra.copy(id = "e2", amountType = "WEIRD")
        val badDeductions = goodExtra.copy(id = "e3", deductions = "X")
        val local = withExtras(goodExtra, badType, badDeductions)

        assertEquals(listOf("e1"), local.toDomain().extras.map { it.id })
    }

    @Test
    fun v9Document_withLegacyFields_migratesToExtras_withTheEnglishNames() {
        val json =
            """{"baseGrossCents":100000,"flexPpm":70000,"flexFirstMonth":2,"flexWithdrawalFeePpm":5000,""" +
                """"profitShareMultiplierPpm":500000,"profitShareMonths":[4,10],"bonusCents":10000,"bonusMonth":5}"""
        val decoded = Json.decodeFromString<LocalSalaryProfile>(json)

        val extras = decoded.toDomain().extras

        assertEquals(listOf("flex", "profitshare", "bonus"), extras.map { it.id })
        assertEquals(listOf("Flexible benefits", "Profit share", "Bonus"), extras.map { it.name })
        assertEquals(setOf(2, 5, 8, 11), extras[0].months)
        assertEquals(5_000, extras[0].feePpm)
        assertEquals(setOf(4, 10), extras[1].months)
        assertEquals(SalaryExtraDeductions.IRS_SS, extras[2].deductions)
        assertEquals(10_000L, extras[2].amount)
    }

    @Test
    fun v9Document_netMode_migratesFlexAsFixed() {
        val json = """{"inputMode":"NET","netMonthlyCents":100000,"flexAnnualCents":100001,"bonusCents":5000}"""

        val extras = Json.decodeFromString<LocalSalaryProfile>(json).toDomain().extras

        assertEquals(listOf(100_001L, 5_000L), extras.map { it.amount })
        assertEquals(SalaryExtraDeductions.NONE, extras[1].deductions)
    }

    @Test
    fun explicitExtras_winOverLegacyFields() {
        val local = valid.copy(extras = listOf(goodExtra), flexPpm = 70_000)

        assertEquals(listOf("e1"), local.toDomain().extras.map { it.id })
    }

    @Test
    fun v10_roundTrip_throughJson_keepsExtras_andLeavesLegacyFieldsOut() {
        val extra = SalaryExtra("a", "Flex", PERCENT, 70_000, setOf(1, 4))
        val profile = SalaryProfile(baseGrossCents = 100_000, extras = listOf(extra))
        val local = profile.toLocal()

        val text =
            Json {
                encodeDefaults = true
                explicitNulls = false
            }.encodeToString(LocalSalaryProfile.serializer(), local)
        val back = Json.decodeFromString<LocalSalaryProfile>(text).toDomain()

        assertEquals(profile, back)
        assertTrue(!text.contains("flexPpm"))
        validateSalaryProfile(local)
    }
}
