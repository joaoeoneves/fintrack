package com.joaoeoneves.fintrack.ui.activity

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Transaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Route-argument seeding, filter/sort changes, and restore of the user's choices. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelFilterTest : ActivityViewModelTestBase() {
    private val coffee = expense("coffee", 450L, now)
    private val food = expense("food", 900L, now.minusSeconds(60)).copy(category = BuiltInCategories.FOOD)
    private val salary = income("salary", 250_000L, now.minusSeconds(120))

    private fun fixture(h: SavedStateHandle = SavedStateHandle()) = Fixture(listOf(coffee, food), listOf(salary), h)

    private fun ids(items: List<Transaction>) = items.map { it.id }

    @Test
    fun defaults_showEverything_dateDescending_grouped() =
        runTest(testDispatcher) {
            val content = content(fixture().vm)

            assertEquals(listOf("coffee", "food", "salary"), ids(content.items))
            assertEquals(
                ActivityFilters(ActivityType.ALL, null, null, "", SortOption.DATE_DESC),
                content.filters,
            )
            assertEquals(1, content.days.size)
        }

    @Test
    fun initialState_isLoading() =
        runTest(testDispatcher) {
            assertEquals(ActivityUiState.Loading, fixture().vm.uiState.value)
        }

    @Test
    fun routeArgs_type_income_expense_andUnknown() =
        runTest(testDispatcher) {
            val cases =
                mapOf("INCOME" to ActivityType.INCOME, "EXPENSE" to ActivityType.EXPENSE, "bogus" to ActivityType.ALL)
            for ((token, expected) in cases) {
                val vm = fixture(args("type" to token)).vm
                assertEquals(token, expected, content(vm).filters.type)
            }
        }

    @Test
    fun routeArgs_incomeType_showsOnlyIncome() =
        runTest(testDispatcher) {
            assertEquals(listOf("salary"), ids(content(fixture(args("type" to "INCOME")).vm).items))
        }

    @Test
    fun routeArgs_category_seedsCategoryFilter_andHidesIncome() =
        runTest(testDispatcher) {
            val vm = fixture(args("category" to "FOOD")).vm
            val content = content(vm)

            assertEquals(CategoryId("FOOD"), vm.categoryFilter.value)
            assertEquals(listOf("food"), ids(content.items))
        }

    @Test
    fun routeArgs_areNotOverwrittenByUserChoices() =
        runTest(testDispatcher) {
            val handle = args("type" to "INCOME", "category" to "FOOD")
            val vm = fixture(handle).vm
            content(vm)

            vm.onTypeSelected(ActivityType.EXPENSE)
            vm.onCategorySelected(null)
            advanceUntilIdle()

            assertEquals("INCOME", handle.get<String>("type"))
            assertEquals("FOOD", handle.get<String>("category"))
            assertEquals(ActivityType.EXPENSE, handle.get<ActivityType>("selectedType"))
            assertEquals(ActivityType.EXPENSE, current(vm).filters.type)
            assertNull(current(vm).filters.category)
        }

    @Test
    fun onTypeSelected_filtersList_andKeepsTotalsUnfiltered() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            val before = content(vm).totals

            vm.onTypeSelected(ActivityType.INCOME)
            advanceUntilIdle()

            assertEquals(listOf("salary"), ids(current(vm).items))
            assertEquals(before, current(vm).totals)
            assertEquals(ActivityType.INCOME, vm.type.value)
        }

    @Test
    fun onQueryChanged_filters_andExposesQueryImmediately() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)

            vm.onQueryChanged("COF")
            assertEquals("COF", vm.query.value)
            advanceUntilIdle()

            assertEquals(listOf("coffee"), ids(current(vm).items))
            assertEquals("COF", current(vm).filters.query)
        }

    @Test
    fun onCategoryLabelsChanged_makesQueryMatchLocalizedLabel() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)
            vm.onQueryChanged("alimentação")
            advanceUntilIdle()
            assertTrue(current(vm).items.isEmpty())

            vm.onCategoryLabelsChanged(mapOf(BuiltInCategories.FOOD.id to "Alimentação"))
            advanceUntilIdle()

            assertEquals(listOf("food"), ids(current(vm).items))
        }

    @Test
    fun onCategorySelected_narrowsExpenses_andNullClears() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)

            vm.onCategorySelected(BuiltInCategories.FOOD.id)
            advanceUntilIdle()
            assertEquals(listOf("food"), ids(current(vm).items))

            vm.onCategorySelected(null)
            advanceUntilIdle()
            assertEquals(3, current(vm).items.size)
        }

    @Test
    fun onCategorySelected_withIncomeType_doesNotHideIncome() =
        runTest(testDispatcher) {
            val vm = fixture(args("type" to "INCOME")).vm
            content(vm)

            vm.onCategorySelected(BuiltInCategories.FOOD.id)
            advanceUntilIdle()

            assertEquals(listOf("salary"), ids(current(vm).items))
        }

    @Test
    fun onSortSelected_amountSorts_areFlat_dateSorts_groupByDay() =
        runTest(testDispatcher) {
            val vm = fixture().vm
            content(vm)

            vm.onSortSelected(SortOption.AMOUNT_DESC)
            advanceUntilIdle()
            assertEquals(listOf("salary", "food", "coffee"), ids(current(vm).items))
            assertTrue(current(vm).days.isEmpty())

            vm.onSortSelected(SortOption.AMOUNT_ASC)
            advanceUntilIdle()
            assertEquals(listOf("coffee", "food", "salary"), ids(current(vm).items))

            vm.onSortSelected(SortOption.DATE_ASC)
            advanceUntilIdle()
            assertEquals(listOf("salary", "food", "coffee"), ids(current(vm).items))
            assertEquals(1, current(vm).days.size)
        }

    @Test
    fun amountSort_usesSignedAmount_soRefundsSortLowest() =
        runTest(testDispatcher) {
            val refund = expense("refund", 100_000L, now, isRefund = true)
            val vm = Fixture(listOf(coffee, refund), listOf(salary)).vm
            content(vm)

            vm.onSortSelected(SortOption.AMOUNT_ASC)
            advanceUntilIdle()

            assertEquals(listOf("refund", "coffee", "salary"), ids(current(vm).items))
        }

    @Test
    fun processDeath_restoresQueryTypeCategoryAndSort() =
        runTest(testDispatcher) {
            val fixture = fixture()
            val original = fixture.vm
            content(original)
            original.onTypeSelected(ActivityType.EXPENSE)
            original.onCategorySelected(BuiltInCategories.FOOD.id)
            original.onQueryChanged("fo")
            original.onSortSelected(SortOption.AMOUNT_ASC)
            advanceUntilIdle()

            val restored = fixture.recreate()
            val content = content(restored)

            assertEquals(
                ActivityFilters(ActivityType.EXPENSE, BuiltInCategories.FOOD.id, null, "fo", SortOption.AMOUNT_ASC),
                content.filters,
            )
            assertEquals("fo", restored.query.value)
            assertEquals(ActivityType.EXPENSE, restored.type.value)
            assertEquals(BuiltInCategories.FOOD.id, restored.categoryFilter.value)
            assertEquals(listOf("food"), ids(content.items))
        }

    // Known production bug: a cleared category is stored as null, so on restore the `?:` fallback
    // re-seeds it from the route's "category" argument. Re-enable once ActivityViewModel is fixed.
    @Test
    fun processDeath_clearedCategory_staysClearedDespiteRouteArgument() =
        runTest(testDispatcher) {
            val fixture = fixture(args("category" to "FOOD"))
            content(fixture.vm)
            fixture.vm.onCategorySelected(null)
            advanceUntilIdle()

            val restored = fixture.recreate()

            assertNull(content(restored).filters.category)
            assertEquals(3, current(restored).items.size)
        }

    @Test
    fun filterAndSortChanges_doNotResubscribeToRepositories() =
        runTest(testDispatcher) {
            val fixture = fixture()
            val vm = fixture.vm
            content(vm)
            val expenseObserves = fixture.expenseRepo.listeners.observedRanges.size
            val incomeObserves = fixture.incomeRepo.listeners.observedRanges.size

            vm.onTypeSelected(ActivityType.EXPENSE)
            vm.onQueryChanged("c")
            vm.onCategorySelected(BuiltInCategories.SHOPPING.id)
            vm.onSortSelected(SortOption.AMOUNT_DESC)
            vm.onCategoryLabelsChanged(mapOf(BuiltInCategories.FOOD.id to "Alimentação"))
            advanceUntilIdle()

            assertEquals(expenseObserves, fixture.expenseRepo.listeners.observedRanges.size)
            assertEquals(incomeObserves, fixture.incomeRepo.listeners.observedRanges.size)
            assertEquals(1, fixture.expenseRepo.listeners.active)
            assertEquals(1, fixture.incomeRepo.listeners.active)
        }
}
