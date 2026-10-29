package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryTotal
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.SpendingSplit
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.ui.dashboard.clampedForChart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** The analytics header numbers ([ActivityUiState.Content.analytics]) of [ActivityViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelAnalyticsTest : ActivityViewModelTestBase() {
    private val food = BuiltInCategories.FOOD
    private val savings = BuiltInCategories.INVESTMENTS

    private fun ActivityUiState.Content.total(category: Category) =
        analytics.breakdown.categoryTotals
            .single { it.category == category }
            .totalCents

    @Test
    fun emptyPeriod_hasZeroSpent_zeroSplit_andIsEmpty() =
        runTest(testDispatcher) {
            val analytics = content(Fixture().vm).analytics

            assertEquals(0L, analytics.spentCents)
            assertEquals(SpendingSplit(0L, 0L, 0L), analytics.split)
            assertFalse(analytics.breakdown.hasExpenses)
            assertFalse(analytics.breakdown.hasEnvelopes)
            assertTrue(analytics.breakdown.isEmpty)
            assertTrue(analytics.envelopeChips.isEmpty())
        }

    @Test
    fun spentCents_excludesSavings_andNetsRefunds() =
        runTest(testDispatcher) {
            val fixture =
                Fixture(
                    listOf(
                        expense("a", 1_000L),
                        expense("r", 300L, isRefund = true),
                        expense("inv", 5_000L, category = savings),
                    ),
                )

            val analytics = content(fixture.vm).analytics

            assertEquals(700L, analytics.spentCents)
            assertEquals(analytics.split.spendingCents, analytics.spentCents)
            // Savings are reported in the list totals, not in the header's spending split.
            assertEquals(0L, analytics.split.savingCents)
            assertEquals(5_000L, content(fixture.vm).totals.savedCents)
        }

    @Test
    fun spentCents_onlySavings_isZero() =
        runTest(testDispatcher) {
            val analytics = content(Fixture(listOf(expense("inv", 5_000L, category = savings))).vm).analytics

            assertEquals(0L, analytics.spentCents)
            assertTrue(analytics.breakdown.hasExpenses)
        }

    @Test
    fun split_separatesRecurringFromManual_andSaving() =
        runTest(testDispatcher) {
            val fixture =
                Fixture(
                    listOf(
                        expense("rec_a_2026-10", 1_200L),
                        expense("manual", 300L),
                        expense("manualRefund", 100L, isRefund = true),
                        expense("rec_inv", 500L, category = savings),
                        expense("old", 9_999L, noonOn(2026, 9, 28)),
                    ),
                )

            val analytics = content(fixture.vm).analytics

            assertEquals(SpendingSplit(1_200L, 200L, 0L), analytics.split)
            assertEquals(1_400L, analytics.spentCents)
        }

    @Test
    fun breakdown_listsEveryActiveCategory_atZero_whenEmpty() =
        runTest(testDispatcher) {
            val content = content(Fixture().vm)

            assertEquals(
                BuiltInCategories.all.filterNot { it.archived },
                content.analytics.breakdown.categoryTotals
                    .map { it.category },
            )
            assertTrue(
                content.analytics.breakdown.categoryTotals
                    .all { it.totalCents == 0L },
            )
        }

    @Test
    fun breakdown_sumsPerCategory_andIgnoresOtherMonths() =
        runTest(testDispatcher) {
            val fixture =
                Fixture(
                    listOf(
                        expense("s1", 1_000L),
                        expense("s2", 500L),
                        expense("f", 800L, category = food),
                        expense("old", 9_999L, noonOn(2026, 9, 28)),
                    ),
                )

            val content = content(fixture.vm)

            assertEquals(1_500L, content.total(BuiltInCategories.SHOPPING))
            assertEquals(800L, content.total(food))
            assertEquals(0L, content.total(BuiltInCategories.TRANSFER))
            assertTrue(content.analytics.breakdown.hasExpenses)
        }

    @Test
    fun refund_reducesCategoryTotal_andMayGoNegative_whileTheChartClampsToZero() =
        runTest(testDispatcher) {
            val fixture =
                Fixture(
                    listOf(
                        expense("small", 1_000L),
                        expense("big", 3_500L, isRefund = true),
                        expense("rent", 20_000L, category = BuiltInCategories.TRANSFER),
                    ),
                )

            val content = content(fixture.vm)

            assertEquals(-2_500L, content.total(BuiltInCategories.SHOPPING))
            val chart =
                content.analytics.breakdown.categoryTotals
                    .clampedForChart()
            assertEquals(0L, chart.single { it.category == BuiltInCategories.SHOPPING }.totalCents)
            assertEquals(20_000L, chart.single { it.category == BuiltInCategories.TRANSFER }.totalCents)
        }

    @Test
    fun zeroAmountExpense_countsAsHavingExpenses() =
        runTest(testDispatcher) {
            val content = content(Fixture(listOf(expense("z", 0L))).vm)

            assertTrue(content.analytics.breakdown.hasExpenses)
            assertEquals(0L, content.analytics.spentCents)
            assertEquals(0L, content.total(BuiltInCategories.SHOPPING))
        }

    @Test
    fun plannedSlices_listOnlyCategoriesWithALimit() =
        runTest(testDispatcher) {
            val budgets =
                FakeBudgetRepository(
                    listOf(
                        Budget(BuiltInCategories.SHOPPING.id, 24_000L),
                        Budget(savings.id, 10_000L),
                        Budget(food.id, 0L),
                    ),
                )

            val breakdown = content(Fixture(budgets = budgets).vm).analytics.breakdown

            assertEquals(
                setOf(CategoryTotal(savings, 10_000L), CategoryTotal(BuiltInCategories.SHOPPING, 24_000L)),
                breakdown.plannedSlices.toSet(),
            )
            assertEquals(2, breakdown.plannedSlices.size)
        }

    @Test
    fun planWithoutExpenses_isNotEmpty() =
        runTest(testDispatcher) {
            val budgets = FakeBudgetRepository(listOf(Budget(BuiltInCategories.SHOPPING.id, 5_000L)))

            val breakdown = content(Fixture(budgets = budgets).vm).analytics.breakdown

            assertFalse(breakdown.hasExpenses)
            assertFalse(breakdown.isEmpty)
        }

    @Test
    fun plannedSlices_followBudgetChangesLive() =
        runTest(testDispatcher) {
            val budgets = FakeBudgetRepository()
            val vm = Fixture(budgets = budgets).vm
            assertTrue(
                content(vm)
                    .analytics.breakdown.plannedSlices
                    .isEmpty(),
            )

            budgets.setBudget(food.id, 7_000L)
            advanceUntilIdle()

            assertEquals(listOf(CategoryTotal(food, 7_000L)), current(vm).analytics.breakdown.plannedSlices)
        }

    @Test
    fun breakdownEnvelopes_followCategoryAssignment() =
        runTest(testDispatcher) {
            val categories = FakeCategoryRepository()
            val envelopes = FakeEnvelopeRepository()
            val foodEnvelope = envelopes.addEnvelope("Food").getOrThrow()
            categories.setGroup(food.id, foodEnvelope)
            val fixture =
                Fixture(
                    listOf(expense("f", 800L, category = food), expense("s", 150L)),
                    categories = categories,
                    envelopes = envelopes,
                )

            val breakdown = content(fixture.vm).analytics.breakdown

            assertTrue(breakdown.hasEnvelopes)
            assertEquals(
                "Food",
                breakdown.envelopes.envelopes
                    .single()
                    .envelope.name,
            )
            assertEquals(
                800L,
                breakdown.envelopes.envelopes
                    .single()
                    .spentCents,
            )
            assertEquals(150L, breakdown.envelopes.unenvelopedSpentCents)
        }

    @Test
    fun analytics_spentFollowsTheList_butBreakdownStaysWholePeriod() =
        runTest(testDispatcher) {
            val fixture =
                Fixture(
                    listOf(expense("a", 1_000L), expense("f", 500L, category = food)),
                    listOf(income("i", 4_000L)),
                )
            val vm = fixture.vm
            val before = content(vm).analytics

            vm.onTypeSelected(ActivityType.INCOME)
            vm.onCategorySelected(food.id)
            vm.onQueryChanged("zzz")
            advanceUntilIdle()

            assertTrue(current(vm).items.isEmpty())
            assertEquals(before.breakdown.envelopes, current(vm).analytics.breakdown.envelopes)
            assertTrue(
                current(vm)
                    .analytics.breakdown.categoryTotals
                    .all { it.totalCents == 0L },
            )
            assertEquals(0L, current(vm).analytics.spentCents)
            assertEquals(1_500L, before.spentCents)
        }

    @Test
    fun analytics_followThePeriod() =
        runTest(testDispatcher) {
            val fixture = Fixture(listOf(expense("oct", 300L), expense("sep", 200L, noonOn(2026, 9, 15))))
            val vm = fixture.vm
            assertEquals(300L, content(vm).analytics.spentCents)

            vm.periodActions.onSelect(Period.Month(YearMonth.of(2026, 9)))
            advanceUntilIdle()
            assertEquals(200L, current(vm).analytics.spentCents)

            vm.periodActions.onSelect(Period.Rolling(TimeRange.THREE_MONTHS))
            advanceUntilIdle()
            assertEquals(500L, current(vm).analytics.spentCents)
        }
}
