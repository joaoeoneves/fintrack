package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.DEFAULT_SS_PPM
import com.joaoeoneves.fintrack.domain.model.SPREAD_FULL_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.testutil.bonusExtra
import com.joaoeoneves.fintrack.testutil.flexExtra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalaryFormParsingTest {
    private fun <T> value(v: T) = Parsed.Value(v)

    @Test
    fun parseCents_dotAndComma() {
        assertEquals(value(123_456L), parseCents("1234.56"))
        assertEquals(value(123_456L), parseCents("1234,56"))
        assertEquals(value(100_000L), parseCents(" 1000 "))
    }

    @Test
    fun parseCents_blankAndZeroAreEmpty() {
        assertEquals(Parsed.Empty, parseCents(""))
        assertEquals(Parsed.Empty, parseCents("   "))
        assertEquals(Parsed.Empty, parseCents("0"))
        assertEquals(Parsed.Empty, parseCents("0,00"))
    }

    @Test
    fun parseCents_garbageNegativeAndOverflowAreInvalid() {
        assertEquals(Parsed.Invalid, parseCents("abc"))
        assertEquals(Parsed.Invalid, parseCents("-5"))
        assertEquals(Parsed.Invalid, parseCents("1.2.3"))
        assertEquals(Parsed.Invalid, parseCents("99999999999999"))
    }

    @Test
    fun parsePercent_valuesInPpm() {
        assertEquals(value(115_000), parsePercentPpm("11.5"))
        assertEquals(value(115_000), parsePercentPpm("11,5"))
        assertEquals(value(1_000_000), parsePercentPpm("100"))
        assertEquals(value(123_457), parsePercentPpm("12.34567"))
        assertEquals(value(1_234), parsePercentPpm(".1234"))
    }

    @Test
    fun parsePercent_zeroIsAValue_blankIsEmpty() {
        assertEquals(value(0), parsePercentPpm("0"))
        assertEquals(Parsed.Empty, parsePercentPpm(""))
    }

    @Test
    fun parsePercent_aboveMaxOrGarbageInvalid() {
        assertEquals(Parsed.Invalid, parsePercentPpm("100.01"))
        assertEquals(Parsed.Invalid, parsePercentPpm("-1"))
        assertEquals(Parsed.Invalid, parsePercentPpm("1e2"))
        assertEquals(Parsed.Invalid, parsePercentPpm("5%"))
        assertEquals(value(500_000), parsePercentPpm("50", maxPercent = 50))
        assertEquals(Parsed.Invalid, parsePercentPpm("51", maxPercent = 50))
    }

    @Test
    fun parseMultiplier_valuesInPpm() {
        assertEquals(value(500_000), parseMultiplierPpm("0.5"))
        assertEquals(value(1_500_000), parseMultiplierPpm("1,5"))
        assertEquals(value(10_000_000), parseMultiplierPpm("10"))
    }

    @Test
    fun parseMultiplier_zeroAndBlankEmpty_aboveTenInvalid() {
        assertEquals(Parsed.Empty, parseMultiplierPpm(""))
        assertEquals(Parsed.Empty, parseMultiplierPpm("0"))
        assertEquals(Parsed.Invalid, parseMultiplierPpm("10.000001"))
        assertEquals(Parsed.Invalid, parseMultiplierPpm("x"))
    }

    @Test
    fun emptyForm_hasNoRequiredAmount_andIsValid() {
        val result = SalaryForm().toResult()

        assertFalse(result.hasRequiredAmount)
        assertTrue(result.invalid.isEmpty())
        assertEquals(DEFAULT_SS_PPM, result.profile.ssPpm)
        assertNull(result.profile.baseGrossCents)
    }

    @Test
    fun grossForm_toProfile() {
        val form =
            SalaryForm(
                base = 100_000L,
                spreadPpm = SPREAD_FULL_PPM,
                irsBase = "10",
                irsSubsidies = "5",
                ss = "12.5",
                exempt = "50",
                meal = 900L,
                extras = listOf(flexExtra(), bonusExtra()),
                // NET-only fields must not leak into a GROSS profile.
                netMonthly = 99_900L,
            )

        val result = form.toResult()

        assertTrue(result.hasRequiredAmount)
        assertTrue(result.invalid.isEmpty())
        assertEquals(
            SalaryProfile(
                inputMode = SalaryInputMode.GROSS,
                baseGrossCents = 100_000,
                subsidySpreadPpm = SPREAD_FULL_PPM,
                irsBasePpm = 100_000,
                irsSubsidiesPpm = 50_000,
                ssPpm = 125_000,
                irsJovemExemptPpm = 500_000,
                mealRatePerDayCents = 900,
                extras = listOf(flexExtra(), bonusExtra()),
            ),
            result.profile,
        )
    }

    @Test
    fun netForm_toProfile_ignoresGrossFields() {
        val form =
            SalaryForm(
                mode = SalaryInputMode.NET,
                netMonthly = 120_000L,
                netHoliday = 60_000L,
                base = 500_000L,
                irsBase = "10",
                extras = listOf(bonusExtra(cents = 5_000)),
            )

        val result = form.toResult()
        val profile = result.profile

        assertTrue(result.hasRequiredAmount)
        assertEquals(SalaryInputMode.NET, profile.inputMode)
        assertEquals(120_000L, profile.netMonthlyCents)
        assertEquals(60_000L, profile.netHolidayLumpCents)
        assertNull(profile.baseGrossCents)
        assertEquals(listOf(bonusExtra(cents = 5_000)), profile.extras)
    }

    @Test
    fun netForm_withoutNet_hasNoRequiredAmount() {
        assertFalse(SalaryForm(mode = SalaryInputMode.NET, base = 100_000L).toResult().hasRequiredAmount)
    }

    @Test
    fun invalidFields_areReported() {
        val form = SalaryForm(base = 0L, irsBase = "120", ss = "x")

        val result = form.toResult()

        assertEquals(
            setOf(SalaryField.IRS_BASE, SalaryField.SS),
            result.invalid,
        )
        assertFalse(result.hasRequiredAmount)
    }

    @Test
    fun blankSs_defaultsToEleven_explicitZeroIsKept() {
        assertEquals(DEFAULT_SS_PPM, SalaryForm(base = 1_000L).toResult().profile.ssPpm)
        assertEquals(0, SalaryForm(base = 1_000L, ss = "0").toResult().profile.ssPpm)
    }

    @Test
    fun toForm_formatsForTheSeparator() {
        val profile =
            SalaryProfile(
                baseGrossCents = 123_456,
                irsBasePpm = 115_000,
                ssPpm = 125_000,
                mealRatePerDayCents = 900,
            )

        val comma = profile.toForm(',')
        val dot = profile.toForm('.')

        assertEquals(123_456L, comma.base)
        assertEquals(123_456L, dot.base)
        assertEquals("11,5", comma.irsBase)
        assertEquals("12.5", dot.ss)
        assertEquals(900L, dot.meal)
    }

    @Test
    fun toForm_defaultsAreBlank() {
        val form = SalaryProfile(baseGrossCents = 100_000).toForm('.')

        assertEquals("", form.ss)
        assertEquals("", form.irsBase)
        assertEquals("", form.irsSubsidies)
        assertEquals("", form.exempt)
        assertTrue(form.extras.isEmpty())
    }

    @Test
    fun formRoundTrip_throughProfile() {
        val profile =
            SalaryProfile(
                baseGrossCents = 123_456,
                subsidySpreadPpm = SPREAD_FULL_PPM,
                irsBasePpm = 115_000,
                irsSubsidiesPpm = 62_334,
                ssPpm = 125_000,
                irsJovemExemptPpm = 250_000,
                mealRatePerDayCents = 905,
                holidaySubsidyMonth = 7,
                extras = listOf(flexExtra(feePpm = 5_000), bonusExtra(month = 9)),
            )

        assertEquals(profile, profile.toForm(',').toResult().profile)
        assertEquals(profile, profile.toForm('.').toResult().profile)
    }

    @Test
    fun netRoundTrip_throughProfile() {
        val profile =
            SalaryProfile(
                inputMode = SalaryInputMode.NET,
                netMonthlyCents = 120_050,
                netHolidayLumpCents = 60_000,
                netChristmasLumpCents = 61_000,
                extras = listOf(bonusExtra(cents = 5_000, month = 2)),
            )

        assertEquals(profile, profile.toForm(',').toResult().profile)
    }

    @Test
    fun subsidyMonthPair_ordersTheTwoMonths() {
        assertEquals(Pair(3, 9), SalaryForm(subsidyMonths = listOf(9, 3)).subsidyMonthPair())
        assertEquals(Pair(3, 9), SalaryForm(subsidyMonths = listOf(3, 9)).subsidyMonthPair())
    }

    @Test
    fun subsidyMonthPair_singleMonthIsUsedTwice_noneGivesDefaults() {
        assertEquals(Pair(5, 5), SalaryForm(subsidyMonths = listOf(5)).subsidyMonthPair())
        assertEquals(Pair(6, 12), SalaryForm(subsidyMonths = emptyList()).subsidyMonthPair())
        assertEquals(listOf(6, 12), SalaryForm().subsidyMonths)
    }

    @Test
    fun subsidyMonths_reachTheProfileOrdered_grossAndNet() {
        val gross = SalaryForm(base = 100_000L, subsidyMonths = listOf(11, 2)).toResult().profile
        val netForm = SalaryForm(mode = SalaryInputMode.NET, netMonthly = 100_000L, subsidyMonths = listOf(4))
        val net = netForm.toResult().profile

        assertEquals(2, gross.holidaySubsidyMonth)
        assertEquals(11, gross.christmasSubsidyMonth)
        assertEquals(4, net.holidaySubsidyMonth)
        assertEquals(4, net.christmasSubsidyMonth)
    }

    @Test
    fun toForm_subsidyMonths_sortedAndCollapsedWhenEqual() {
        val swapped = SalaryProfile(holidaySubsidyMonth = 11, christmasSubsidyMonth = 2)
        val same = SalaryProfile(holidaySubsidyMonth = 4, christmasSubsidyMonth = 4)

        assertEquals(listOf(2, 11), swapped.toForm('.').subsidyMonths)
        assertEquals(listOf(4), same.toForm('.').subsidyMonths)
    }

    @Test
    fun toResult_ignoresMealSkipMonths() {
        assertTrue(
            SalaryForm(base = 1_000L)
                .toResult()
                .profile.mealSkipMonths
                .isEmpty(),
        )
    }

    @Test
    fun toggleMonth_withMax_replacesTheOldest() {
        assertEquals(listOf(2, 3), toggleMonth(listOf(1, 2), 3, 2))
        assertEquals(listOf(2), toggleMonth(listOf(1), 2, 1))
        assertEquals(listOf(1, 2), toggleMonth(listOf(1), 2, 2))
    }

    @Test
    fun toggleMonth_deselects_butNeverTheLastOne() {
        assertEquals(listOf(2), toggleMonth(listOf(1, 2), 1, 2))
        assertEquals(listOf(5), toggleMonth(listOf(5), 5, 2))
        assertEquals(listOf(5), toggleMonth(listOf(5), 5, null))
    }

    @Test
    fun toggleMonth_withoutMax_accumulates() {
        assertEquals(listOf(1, 2, 3, 4), toggleMonth(listOf(1, 2, 3), 4, null))
        assertEquals(listOf(1, 3), toggleMonth(listOf(1, 2, 3), 2, null))
    }
}
