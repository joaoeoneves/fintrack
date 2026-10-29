package com.joaoeoneves.fintrack.ui.expense.addedit

import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.MANAGED_MEAL
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** [mealCardPlafondUsedIn]: true once the month's meal-card balance is 0 or less. */
class MealCardPlafondUsedTest {
    private val utc = ZoneOffset.UTC
    private val oct = YearMonth.of(2026, 10)
    private val mealLine = PlannedIncome("meal", "Meal allowance", 20_000L, 0, managedKey = MANAGED_MEAL)

    private fun at(iso: String) = Instant.parse(iso)

    private val septPlafond = Income("pay_meal_202609", "Meal allowance", 20_000L, at("2026-09-29T12:00:00Z"), null)

    private fun card(
        id: String,
        cents: Long,
        date: String = "2026-10-05T12:00:00Z",
        refund: Boolean = false,
    ): Expense = testExpense(id, cents, at(date), isRefund = refund).copy(paidWithMealCard = true)

    private suspend fun used(
        expenses: List<Expense>,
        incomes: List<Income> = listOf(septPlafond),
        lines: List<PlannedIncome> = listOf(mealLine),
        month: YearMonth = oct,
    ) = mealCardPlafondUsedIn(
        FakeExpenseRepository(expenses),
        FakeIncomeRepository(incomes),
        FakePlannedIncomeRepository(lines),
        utc,
    )(month).first()

    @Test
    fun balanceLeft_isNotUsed() =
        runTest {
            assertFalse(used(listOf(card("a", 19_999))))
        }

    @Test
    fun balanceExactlyZero_isUsed() =
        runTest {
            assertTrue(used(listOf(card("a", 12_000), card("b", 8_000))))
        }

    @Test
    fun overspent_isUsed() =
        runTest {
            assertTrue(used(listOf(card("a", 25_000))))
        }

    @Test
    fun aRefund_givesTheBalanceBack() =
        runTest {
            assertFalse(used(listOf(card("a", 20_000), card("r", 1_000, refund = true))))
        }

    @Test
    fun bankExpensesAndOtherMonths_doNotCount() =
        runTest {
            val bank = testExpense("bank", 30_000, at("2026-10-06T12:00:00Z"))
            val september = card("sep", 30_000, date = "2026-09-30T12:00:00Z")
            assertFalse(used(listOf(bank, september)))
        }

    @Test
    fun noPlafondReceived_countsAsUsed() =
        runTest {
            assertTrue(used(emptyList(), incomes = emptyList()))
        }

    @Test
    fun aFailingRead_isNotUsed() =
        runTest {
            val failing = FakeExpenseRepository(nextObserveExpensesError = IllegalStateException("down"))
            val flow =
                mealCardPlafondUsedIn(
                    failing,
                    FakeIncomeRepository(),
                    FakePlannedIncomeRepository(),
                    utc,
                )(oct)
            assertFalse(flow.first())
        }
}
