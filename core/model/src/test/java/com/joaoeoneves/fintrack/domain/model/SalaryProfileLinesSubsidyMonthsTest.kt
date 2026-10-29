package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.data.FakeSalaryLineLabelsProvider
import org.junit.Assert.assertEquals
import org.junit.Test

/** The holiday and Christmas lines each keep their own label and month, even when the months are inverted. */
class SalaryProfileLinesSubsidyMonthsTest {
    private val en = FakeSalaryLineLabelsProvider.ENGLISH

    private fun lines(
        holiday: Int,
        christmas: Int,
    ) = salaryProfileLines(
        SalaryProfile(
            baseGrossCents = 100_000,
            irsBasePpm = 100_000,
            holidaySubsidyMonth = holiday,
            christmasSubsidyMonth = christmas,
        ),
        emptyList(),
        en,
    )

    private fun List<PlannedIncome>.line(key: String) = single { it.managedKey == key }

    @Test
    fun invertedMonths_eachLineKeepsItsOwnLabelAndMonth() {
        val result = lines(holiday = 11, christmas = 3)

        assertEquals(11, result.line(MANAGED_HOLIDAY).monthOfYear)
        assertEquals(en.holidaySubsidy, result.line(MANAGED_HOLIDAY).label)
        assertEquals(3, result.line(MANAGED_CHRISTMAS).monthOfYear)
        assertEquals(en.christmasSubsidy, result.line(MANAGED_CHRISTMAS).label)
    }

    @Test
    fun sameMonth_bothLinesLandThere() {
        val result = lines(holiday = 7, christmas = 7)

        assertEquals(listOf(7, 7), listOf(MANAGED_HOLIDAY, MANAGED_CHRISTMAS).map { result.line(it).monthOfYear })
    }

    @Test
    fun defaultMonths_areUnchanged() {
        val result = lines(DEFAULT_HOLIDAY_SUBSIDY_MONTH, DEFAULT_CHRISTMAS_SUBSIDY_MONTH)

        assertEquals(6, result.line(MANAGED_HOLIDAY).monthOfYear)
        assertEquals(12, result.line(MANAGED_CHRISTMAS).monthOfYear)
    }
}
