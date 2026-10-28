package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** [PeriodOverview.periodEnvelopes]: spend per envelope over the whole period, with no planned amounts. */
class ObservePeriodOverviewPeriodEnvelopesTest : ObservePeriodOverviewTestBase() {
    private val august = YearMonth.of(2026, 8)

    @Test
    fun noGroupsOrExpenses_isEmpty() =
        runTest {
            assertTrue(overview(Period.CurrentMonth).periodEnvelopes.envelopes.isEmpty())
        }

    @Test
    fun rollingThreeMonths_sumsSpendPerGroupAcrossMonths() =
        runTest {
            val food = groups.addEnvelope("Food").getOrThrow()
            val shopping = groups.addEnvelope("Fun").getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)
            categories.setGroup(BuiltInCategories.SHOPPING.id, shopping)
            expenses.delegate.addExpense(testExpense("f1", 1_000L, noonOn(august, 25), BuiltInCategories.FOOD))
            expenses.delegate.addExpense(testExpense("f2", 2_000L, noonOn(september, 10), BuiltInCategories.FOOD))
            expenses.delegate.addExpense(testExpense("f3", 400L, noonOn(october, 3), BuiltInCategories.FOOD))
            expenses.delegate.addExpense(testExpense("s1", 750L, noonOn(september, 12), BuiltInCategories.SHOPPING))
            expenses.delegate.addExpense(testExpense("s2", 50L, noonOn(october, 4), BuiltInCategories.SHOPPING))

            val result = overview(Period.Rolling(TimeRange.THREE_MONTHS)).periodEnvelopes.envelopes

            assertEquals(3_400L, result.single { it.envelope.name == "Food" }.spentCents)
            assertEquals(800L, result.single { it.envelope.name == "Fun" }.spentCents)
            assertTrue(result.all { it.amountCents == null })
        }

    @Test
    fun currentMonth_excludesEarlierMonths() =
        runTest {
            val food = groups.addEnvelope("Food").getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)
            expenses.delegate.addExpense(testExpense("old", 9_000L, noonOn(september, 28), BuiltInCategories.FOOD))
            expenses.delegate.addExpense(testExpense("new", 300L, noonOn(october, 2), BuiltInCategories.FOOD))

            val result = overview(Period.CurrentMonth).periodEnvelopes.envelopes

            assertEquals(300L, result.single { it.envelope.name == "Food" }.spentCents)
        }

    @Test
    fun unenvelopedCategories_goUnderUnenveloped() =
        runTest {
            val food = groups.addEnvelope("Food").getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)
            expenses.delegate.addExpense(testExpense("f", 500L, noonOn(october, 2), BuiltInCategories.FOOD))
            expenses.delegate.addExpense(testExpense("s1", 200L, noonOn(september, 20), BuiltInCategories.SHOPPING))
            expenses.delegate.addExpense(testExpense("s2", 100L, noonOn(october, 6), BuiltInCategories.SHOPPING))

            val result = overview(Period.Rolling(TimeRange.THREE_MONTHS)).periodEnvelopes.envelopes

            assertEquals(1, result.size)
            assertEquals(500L, result.first().spentCents)
            assertEquals(300L, overview(Period.Rolling(TimeRange.THREE_MONTHS)).periodEnvelopes.unenvelopedSpentCents)
        }

    @Test
    fun refund_reducesGroupSpend() =
        runTest {
            val food = groups.addEnvelope("Food").getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)
            expenses.delegate.addExpense(testExpense("f", 1_000L, noonOn(october, 2), BuiltInCategories.FOOD))
            expenses.delegate.addExpense(
                testExpense("r", 250L, noonOn(october, 3), BuiltInCategories.FOOD, isRefund = true),
            )

            assertEquals(
                750L,
                overview(Period.CurrentMonth)
                    .periodEnvelopes.envelopes
                    .single()
                    .spentCents,
            )
        }

    @Test
    fun plannedBudgets_doNotLeakIntoPeriodEnvelopes() =
        runTest {
            val food = groups.addEnvelope("Food").getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)
            budgets.delegate.setBudget(BuiltInCategories.FOOD.id, 16_000L)
            expenses.delegate.addExpense(testExpense("f", 100L, noonOn(october, 2), BuiltInCategories.FOOD))

            val result = overview(Period.CurrentMonth)

            assertEquals(
                16_000L,
                result.envelopes.envelopes
                    .single()
                    .amountCents,
            )
            assertNull(
                result.periodEnvelopes.envelopes
                    .single()
                    .amountCents,
            )
        }
}
