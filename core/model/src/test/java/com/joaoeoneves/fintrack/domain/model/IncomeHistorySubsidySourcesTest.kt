package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testIncome
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** Holiday, Christmas and duodecimos are three separate income sources, not one "subsidies" source. */
class IncomeHistorySubsidySourcesTest {
    private val months = (1..12).map { YearMonth.of(2026, it) }
    private val holiday =
        PlannedIncome(
            MANAGED_HOLIDAY,
            "Holiday",
            70_000L,
            0,
            frequency = IncomeFrequency.YEARLY,
            monthOfYear = 6,
            managedKey = MANAGED_HOLIDAY,
        )
    private val christmas =
        PlannedIncome(
            MANAGED_CHRISTMAS,
            "Christmas",
            80_000L,
            1,
            frequency = IncomeFrequency.YEARLY,
            monthOfYear = 11,
            managedKey = MANAGED_CHRISTMAS,
        )
    private val duodecimos = PlannedIncome(MANAGED_DUODECIMOS, "Duodecimos", 5_000L, 2, managedKey = MANAGED_DUODECIMOS)
    private val lines = listOf(holiday, christmas, duodecimos)

    private fun at(month: Int) = Instant.parse("2026-%02d-20T12:00:00Z".format(month))

    private val history =
        incomeHistoryOf(
            months,
            listOf(testIncome("pay_${MANAGED_HOLIDAY}_202606", 70_000L, at(6))),
            lines,
            IncomeMonth { 20 },
            ZoneOffset.UTC,
            YearMonth.of(2026, 12),
        )

    @Test
    fun eachSubsidyIsItsOwnSourceKey() {
        assertEquals(
            listOf(SalaryComponent.SUBSIDIES, SalaryComponent.HOLIDAY, SalaryComponent.CHRISTMAS).toSet(),
            history.mapNotNull { (it.key as? IncomeSourceKey.Row)?.component }.toSet(),
        )
        assertEquals(3, history.size)
    }

    @Test
    fun aHolidayPayment_isReceivedOnlyByTheHolidaySource() {
        val byComponent = history.associateBy { (it.key as IncomeSourceKey.Row).component }

        assertEquals(70_000L, byComponent.getValue(SalaryComponent.HOLIDAY).receivedCents)
        assertEquals(0L, byComponent.getValue(SalaryComponent.CHRISTMAS).receivedCents)
        assertEquals(0L, byComponent.getValue(SalaryComponent.SUBSIDIES).receivedCents)
    }

    @Test
    fun christmasExpectsItsAmountOnlyInItsOwnMonth() {
        val source = history.single { (it.key as IncomeSourceKey.Row).component == SalaryComponent.CHRISTMAS }
        val christmasMonths = source.months

        assertEquals(80_000L, christmasMonths.single { it.month.monthValue == 11 }.expectedCents)
        assertEquals(0L, christmasMonths.filter { it.month.monthValue != 11 }.sumOf { it.expectedCents ?: 0L })
    }
}
