package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MANAGED_KEYS
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_PROFIT_SHARE_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

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
        validateSalaryProfile(valid.copy(profitShareMultiplierPpm = MAX_PROFIT_SHARE_PPM))
        validateSalaryProfile(valid.copy(profitShareMultiplierPpm = 0))
        invalid(valid.copy(profitShareMultiplierPpm = MAX_PROFIT_SHARE_PPM + 1))
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
                flexPpm = 10,
                flexAnnualCents = 11,
                flexFirstMonth = 4,
                flexWithdrawalFeePpm = 12,
                profitShareMultiplierPpm = 13,
                profitShareMonth = 5,
                bonusCents = 14,
                bonusMonth = 6,
            )

        assertEquals(profile, profile.toLocal().toDomain())
        assertEquals(listOf(1, 3), profile.toLocal().mealSkipMonths)
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
        MANAGED_KEYS.forEach { validatePlannedIncome(LocalPlannedIncome("p", "Pay", 5, 0, managedKey = it)) }
    }

    @Test
    fun managedKey_absentIsValid_unknownRejected() {
        validatePlannedIncome(LocalPlannedIncome("p", "Pay", 5, 0))
        assertThrows(IllegalArgumentException::class.java) {
            validatePlannedIncome(LocalPlannedIncome("p", "Pay", 5, 0, managedKey = "profile_other"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validatePlannedIncome(LocalPlannedIncome("p", "Pay", 5, 0, managedKey = ""))
        }
    }

    @Test
    fun managedKey_mapsBothWays() {
        val local = LocalPlannedIncome("p", "Pay", 5, 0, managedKey = MANAGED_KEYS.first())

        assertEquals(MANAGED_KEYS.first(), local.toDomain().managedKey)
        assertEquals(local, local.toDomain().toLocal())
        assertNull(LocalPlannedIncome("p", "Pay", 5, 0).toDomain().managedKey)
    }
}
