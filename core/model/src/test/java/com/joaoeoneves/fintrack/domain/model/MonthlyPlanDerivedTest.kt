package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Derived plan figures, [plannedSlices] and [dailyAllowanceOf]. */
class MonthlyPlanDerivedTest : MonthlyPlanTestBase() {
    private val food = BuiltInCategories.FOOD
    private val shopping = BuiltInCategories.SHOPPING
    private val investments = BuiltInCategories.INVESTMENTS
    private val october = YearMonth.of(2026, 10)

    @Test
    fun remainingInBuckets_excludesSavingAndUnbudgeted() {
        val result =
            plan(
                listOf(
                    bucket(food, 30_000L, 10_000L),
                    bucket(shopping, null, 5_000L),
                    bucket(investments, 50_000L, 0L),
                ),
            )

        assertEquals(20_000L, result.remainingInBucketsCents)
    }

    @Test
    fun plannedAllocationsAndLeftover() {
        val result = plan(listOf(bucket(food, 30_000L), bucket(investments, 50_000L)))

        assertEquals(30_000L, result.plannedSpendingCents)
        assertEquals(50_000L, result.plannedSavingCents)
        assertEquals(80_000L, result.plannedAllocationsCents)
        assertEquals(120_000L, result.plannedLeftoverCents)
    }

    @Test
    fun plannedLeftover_canBeNegative_andIgnoresActualIncome() {
        val result = plan(listOf(bucket(food, 250_000L)), income = listOf(900_000L))

        assertEquals(-50_000L, result.plannedLeftoverCents)
    }

    @Test
    fun plannedSlices_onlyPositiveLimits_keepingOrder() {
        val buckets =
            listOf(
                bucket(BuiltInCategories.TRANSFER, null, 1L),
                bucket(investments, 50_000L),
                bucket(shopping, 0L),
                bucket(food, 30_000L, 99L),
            )

        assertEquals(
            listOf(CategoryTotal(investments, 50_000L), CategoryTotal(food, 30_000L)),
            buckets.plannedSlices(),
        )
    }

    @Test
    fun plannedSlices_emptyWhenNothingPlanned() {
        assertEquals(emptyList<CategoryTotal>(), emptyList<CategoryBudget>().plannedSlices())
    }

    @Test
    fun dailyAllowance_firstDay_spreadsOverTheWholeMonth() {
        val result = dailyAllowanceOf(310_000L, october, LocalDate.of(2026, 10, 1))

        assertEquals(DailyAllowance(cents = 10_000L, daysLeft = 31), result)
    }

    @Test
    fun dailyAllowance_lastDay_isOneDayLeft() {
        val result = dailyAllowanceOf(5_000L, october, LocalDate.of(2026, 10, 31))

        assertEquals(DailyAllowance(cents = 5_000L, daysLeft = 1), result)
    }

    @Test
    fun dailyAllowance_otherMonth_isNull() {
        assertNull(dailyAllowanceOf(5_000L, YearMonth.of(2026, 9), LocalDate.of(2026, 10, 5)))
        assertNull(dailyAllowanceOf(5_000L, YearMonth.of(2026, 11), LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun dailyAllowance_zeroOrNegativeSafe_isNull() {
        assertNull(dailyAllowanceOf(0L, october, LocalDate.of(2026, 10, 5)))
        assertNull(dailyAllowanceOf(-1L, october, LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun dailyAllowance_roundsDown() {
        // 10 days left (Oct 22..31): 1_009 / 10 = 100.
        assertEquals(DailyAllowance(100L, 10), dailyAllowanceOf(1_009L, october, LocalDate.of(2026, 10, 22)))
        // Less than one cent per day rounds to zero but is still reported.
        assertEquals(DailyAllowance(0L, 31), dailyAllowanceOf(30L, october, LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun dailyAllowance_leapFebruary_has29Days() {
        val result = dailyAllowanceOf(2_900L, YearMonth.of(2028, 2), LocalDate.of(2028, 2, 1))

        assertEquals(DailyAllowance(100L, 29), result)
    }
}
