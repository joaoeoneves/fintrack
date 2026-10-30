package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** [plannedForCategory]: recurring-only planning and which recurring items count. */
class PlannedForCategoryTest {
    private val october = YearMonth.of(2026, 10)

    private fun planned(
        month: YearMonth = october,
        vararg items: RecurringItem,
    ) = plannedForCategory(month, items.toList())

    @Test
    fun nothing_isNone() {
        assertEquals(PlannedAmount(null, PlannedSource.NONE), planned())
    }

    @Test
    fun monthlyItems_areSummed() {
        val result =
            planned(items = arrayOf(recurringItem("a", amountCents = 1_000L), recurringItem("b", amountCents = 250L)))

        assertEquals(PlannedAmount(1_250L, PlannedSource.RECURRING), result)
    }

    @Test
    fun yearlyItem_countsOnlyInItsMonth() {
        val yearly = recurringItem(frequency = RecurringFrequency.YEARLY, monthOfYear = 10, amountCents = 12_000L)

        assertEquals(PlannedAmount(12_000L, PlannedSource.RECURRING), planned(october, items = arrayOf(yearly)))
        assertEquals(PlannedSource.NONE, planned(YearMonth.of(2026, 9), items = arrayOf(yearly)).source)
        assertEquals(12_000L, planned(YearMonth.of(2027, 10), items = arrayOf(yearly)).cents)
    }

    @Test
    fun yearlyAndMonthlyItems_addUpInTheYearlyMonth() {
        val yearly = recurringItem("y", frequency = RecurringFrequency.YEARLY, monthOfYear = 10, amountCents = 12_000L)
        val monthly = recurringItem("m", amountCents = 500L)

        assertEquals(12_500L, planned(october, items = arrayOf(yearly, monthly)).cents)
        assertEquals(500L, planned(YearMonth.of(2026, 11), items = arrayOf(yearly, monthly)).cents)
    }

    @Test
    fun pausedItem_isExcluded() {
        assertEquals(PlannedSource.NONE, planned(items = arrayOf(recurringItem(active = false))).source)
    }

    @Test
    fun endedItem_isExcluded_butCountsInItsLastMonth() {
        val ended = recurringItem(endDate = day(2026, 9, 30))
        val endsInOctober = recurringItem(endDate = day(2026, 10, 15))

        assertEquals(PlannedSource.NONE, planned(october, items = arrayOf(ended)).source)
        assertEquals(PlannedSource.RECURRING, planned(october, items = arrayOf(endsInOctober)).source)
        assertEquals(PlannedSource.NONE, planned(YearMonth.of(2026, 11), items = arrayOf(endsInOctober)).source)
    }

    @Test
    fun endDateBeforeBillingDay_excludesThatMonth() {
        val item = recurringItem(dayOfMonth = 20, endDate = day(2026, 10, 10))

        assertEquals(PlannedSource.NONE, planned(october, items = arrayOf(item)).source)
    }

    @Test
    fun notYetStartedItem_isExcluded() {
        val future = recurringItem(startDate = day(2026, 12, 1))

        assertEquals(PlannedSource.NONE, planned(october, items = arrayOf(future)).source)
        assertEquals(PlannedSource.RECURRING, planned(YearMonth.of(2026, 12), items = arrayOf(future)).source)
    }

    @Test
    fun incomeItems_areIgnored() {
        val salary = recurringItem(type = RecurringType.INCOME, amountCents = 200_000L)

        assertEquals(PlannedSource.NONE, planned(items = arrayOf(salary)).source)
    }
}
