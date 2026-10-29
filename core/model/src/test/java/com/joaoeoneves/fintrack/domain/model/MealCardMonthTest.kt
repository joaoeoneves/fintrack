package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

/** The meal card: plafond = meal income owned by month-1, spent = card-flagged expenses dated in the month. */
class MealCardMonthTest {
    private val utc = ZoneOffset.UTC
    private val meal = PlannedIncome("meal", "Meal allowance", 20_000L, 1, managedKey = MANAGED_MEAL)
    private val salary = PlannedIncome("sal", "Salary", 100_000L, 0, managedKey = MANAGED_NET_SALARY)
    private val lines = listOf(meal, salary)

    private fun at(
        y: Int,
        m: Int,
        d: Int,
    ): Instant = LocalDate.of(y, m, d).atTime(LocalTime.NOON).toInstant(utc)

    private fun mealPay(
        month: YearMonth,
        cents: Long,
        date: Instant,
    ) = Income(paydayIncomeId("meal", month), "Meal allowance", cents, date, null)

    private fun card(
        id: String,
        cents: Long,
        date: Instant,
        refund: Boolean = false,
    ) = testExpense(id, cents, date, isRefund = refund).copy(paidWithMealCard = true)

    @Test
    fun plafond_isMealIncomeOwnedByPreviousMonth_ignoringSalaryAndOtherMonths() {
        val incomes =
            listOf(
                mealPay(YearMonth.of(2026, 9), 20_000, at(2026, 9, 29)),
                mealPay(YearMonth.of(2026, 10), 25_000, at(2026, 10, 30)),
                Income(paydayIncomeId("sal", YearMonth.of(2026, 9)), "Salary", 100_000, at(2026, 9, 29), null),
            )
        val october = mealCardMonthOf(YearMonth.of(2026, 10), incomes, emptyList(), lines, utc)
        assertEquals(20_000L, october.receivedCents)
        assertEquals(25_000L, mealCardMonthOf(YearMonth.of(2026, 11), incomes, emptyList(), lines, utc).receivedCents)
    }

    @Test
    fun spent_countsOnlyCardFlaggedExpensesOfTheMonth_andRefundsNetOut() {
        val expenses =
            listOf(
                card("a", 5_000, at(2026, 10, 3)),
                card("b", 1_500, at(2026, 10, 10), refund = true),
                card("prev", 999, at(2026, 9, 30)),
                testExpense("bank", 7_000, at(2026, 10, 4)),
            )
        val result = mealCardMonthOf(YearMonth.of(2026, 10), emptyList(), expenses, lines, utc)
        assertEquals(3_500L, result.spentCents)
        assertEquals(-3_500L, result.balanceCents)
    }

    @Test
    fun january_usesThePreviousDecemberPlafond_evenWhenPaidInJanuary() {
        val decPaidJan2 = mealPay(YearMonth.of(2026, 12), 20_000, at(2027, 1, 2))
        val january = mealCardMonthOf(YearMonth.of(2027, 1), listOf(decPaidJan2), emptyList(), lines, utc)
        assertEquals(20_000L, january.receivedCents)
    }

    @Test
    fun year_isTheSumOfItsTwelveMonths_januaryFromPreviousDecember() {
        val incomes =
            listOf(
                mealPay(YearMonth.of(2025, 12), 10_000, at(2025, 12, 30)),
                mealPay(YearMonth.of(2026, 1), 20_000, at(2026, 1, 30)),
                mealPay(YearMonth.of(2026, 12), 30_000, at(2026, 12, 30)),
            )
        val expenses = listOf(card("a", 4_000, at(2026, 1, 5)), card("b", 6_000, at(2026, 12, 5)))
        val year = mealCardYearOf(2026, incomes, expenses, lines, utc)
        // Jan gets Dec 2025 (10k) and Feb gets Jan (20k); Dec 2026's own payment funds 2027 and is not counted.
        assertEquals(30_000L, year.receivedCents)
        assertEquals(10_000L, year.spentCents)
        assertEquals(
            (1..12).sumOf { mealCardMonthOf(YearMonth.of(2026, it), incomes, expenses, lines, utc).receivedCents },
            year.receivedCents,
        )
    }
}
