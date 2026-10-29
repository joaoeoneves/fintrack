package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.IncomeSourceKey
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.ui.activity.analytics.encode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Income source filter, shifted incomes and the attached monthly history of [ActivityViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelIncomeTest : ActivityViewModelTestBase() {
    private val rent = PlannedIncome("rent", "Rent", 20_000L, 1)
    private val rentKey = IncomeSourceKey.Row(null, null, "rent").encode()
    private val rentPay = income("pay_rent_202610", 20_000L, noonOn(2026, 10, 5))
    private val gift = income("gift", 5_000L, noonOn(2026, 10, 6)).copy(source = "Gift")
    private val giftKey = IncomeSourceKey.Named("gift").encode()

    private suspend fun fixture(): Fixture {
        val fixture = Fixture(emptyList(), listOf(rentPay, gift))
        fixture.plannedIncomeRepo.savePlannedIncome(rent)
        return fixture
    }

    private fun ids(content: ActivityUiState.Content) = content.items.map { it.id }

    @Test
    fun incomeSourceFilter_onIncomeTab_keepsOnlyThatSource() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            vm.onTypeSelected(ActivityType.INCOME)
            vm.onIncomeSourceSelected(rentKey)
            advanceUntilIdle()

            val c = current(vm)
            assertEquals(listOf("pay_rent_202610"), ids(c))
            assertEquals(rentKey, c.filters.incomeSource)

            vm.onIncomeSourceSelected(giftKey)
            advanceUntilIdle()
            assertEquals(listOf("gift"), ids(current(vm)))

            vm.onIncomeSourceSelected(null)
            advanceUntilIdle()
            assertEquals(setOf("pay_rent_202610", "gift"), ids(current(vm)).toSet())
        }

    @Test
    fun incomeSourceFilter_notAppliedOutsideTheIncomeTab() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            vm.onIncomeSourceSelected(rentKey)
            advanceUntilIdle()

            assertEquals(setOf("pay_rent_202610", "gift"), ids(current(vm)).toSet())
        }

    @Test
    fun staleIncomeSourceKey_isIgnored_andClearedFromFilters() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            vm.onTypeSelected(ActivityType.INCOME)
            vm.onIncomeSourceSelected("named:no-longer-there")
            advanceUntilIdle()

            val c = current(vm)
            assertEquals(setOf("pay_rent_202610", "gift"), ids(c).toSet())
            assertNull(c.filters.incomeSource)
        }

    @Test
    fun incomeSourceSelection_isRestoredFromTheSavedState() =
        runTest(testDispatcher) {
            val f = fixture()
            f.vm.onIncomeSourceSelected(rentKey)
            f.vm.onTypeSelected(ActivityType.INCOME)

            assertEquals(listOf("pay_rent_202610"), ids(content(f.recreate())))
        }

    @Test
    fun filtersOnlyChange_doesNotResubscribeHistoryOrPeriod() =
        runTest(testDispatcher) {
            val f = fixture()
            content(f.vm)
            val expenseRanges = f.expenseRepo.listeners.observedRanges.size
            val incomeRanges = f.incomeRepo.listeners.observedRanges.size

            f.vm.onQueryChanged("gift")
            f.vm.onTypeSelected(ActivityType.INCOME)
            f.vm.onIncomeSourceSelected(giftKey)
            f.vm.onSortSelected(SortOption.AMOUNT_DESC)
            advanceUntilIdle()

            assertEquals(expenseRanges, f.expenseRepo.listeners.observedRanges.size)
            assertEquals(incomeRanges, f.incomeRepo.listeners.observedRanges.size)
        }

    @Test
    fun periodChange_resubscribesHistoryForTheNewEndMonth() =
        runTest(testDispatcher) {
            val f = fixture()
            content(f.vm)
            val before = f.expenseRepo.listeners.observedRanges.size

            f.vm.periodActions.onPrevious()
            advanceUntilIdle()

            assertTrue(f.expenseRepo.listeners.observedRanges.size > before)
        }
}
