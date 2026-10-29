package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.IncomeSourceKey
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.ui.activity.analytics.encode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The one-line summary, the Fonte chips and stale-key handling of [ActivityViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelSummaryTest : ActivityViewModelTestBase() {
    private val rent = PlannedIncome("rent", "Rent", 20_000L, 1)
    private val rentKey = IncomeSourceKey.Row(null, null, "rent").encode()
    private val giftKey = IncomeSourceKey.Named("gift").encode()
    private val rentPay = income("pay_rent_202610", 20_000L, noonOn(2026, 10, 5))
    private val gift = income("gift", 5_000L, noonOn(2026, 10, 6)).copy(source = "Gift")
    private val shop = expense("shop", 1_000L, noonOn(2026, 10, 3))
    private val refund = expense("refund", 200L, noonOn(2026, 10, 4), isRefund = true)
    private val invest = expense("inv", 3_000L, noonOn(2026, 10, 7), category = BuiltInCategories.INVESTMENTS)

    private suspend fun fixture(): Fixture {
        val fixture = Fixture(listOf(shop, refund, invest), listOf(rentPay, gift))
        fixture.plannedIncomeRepo.savePlannedIncome(rent)
        return fixture
    }

    @Test
    fun summary_monthWithoutFilters_receivedIsTheMonthIncome_spentExcludesInvestments() =
        runTest(testDispatcher) {
            val summary = content(fixture().vm).summary

            assertEquals(25_000L, summary.receivedCents)
            // 1000 shop - 200 refund; the 3000 investment is saved, not spent.
            assertEquals(800L, summary.spentCents)
            assertEquals(3_000L, summary.savedCents)
        }

    @Test
    fun summary_queryFilter_receivedFollowsTheVisibleList() =
        runTest(testDispatcher) {
            val f = fixture()
            content(f.vm)
            f.vm.onQueryChanged("gift")
            advanceUntilIdle()

            assertEquals(5_000L, current(f.vm).summary.receivedCents)
        }

    @Test
    fun summary_sourceFilter_receivedIsThatSourceOnly() =
        runTest(testDispatcher) {
            val f = fixture()
            content(f.vm)
            f.vm.onTypeSelected(ActivityType.INCOME)
            f.vm.onIncomeSourceSelected(rentKey)
            advanceUntilIdle()

            assertEquals(20_000L, current(f.vm).summary.receivedCents)
        }

    @Test
    fun summary_customRange_countsByDate() =
        runTest(testDispatcher) {
            val f = fixture()
            content(f.vm)
            f.vm.periodActions.onSelect(Period.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)))
            advanceUntilIdle()

            val summary = current(f.vm).summary
            assertEquals(20_000L, summary.receivedCents)
            assertEquals(800L, summary.spentCents)
            assertEquals(0L, summary.savedCents)
        }

    @Test
    fun summary_emptyPeriod_isAllZero() =
        runTest(testDispatcher) {
            val vm = Fixture().vm

            assertEquals(ActivitySummary(0L, 0L, 0L), content(vm).summary)
        }

    @Test
    fun incomeSources_biggestFirst_withLineLabelAndLatestSpelling() =
        runTest(testDispatcher) {
            val chips = content(fixture().vm).incomeSources

            assertEquals(listOf(rentKey, giftKey), chips.map { it.key })
            assertEquals(listOf("Rent", "Gift"), chips.map { it.label })
        }

    @Test
    fun staleSourceKey_isIgnored_andDoesNotFilter() =
        runTest(testDispatcher) {
            val f = fixture()
            content(f.vm)
            f.vm.onTypeSelected(ActivityType.INCOME)
            f.vm.onIncomeSourceSelected("named:does-not-exist")
            advanceUntilIdle()

            val c = current(f.vm)
            assertNull(c.filters.incomeSource)
            assertEquals(setOf("pay_rent_202610", "gift"), c.items.map { it.id }.toSet())
        }
}
