package com.joaoeoneves.fintrack.ui.recurring

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.userCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditRecurringViewModelTest : AddEditRecurringViewModelTestBase() {
    @Test
    fun newItem_defaultsToRecurringCategoryAndToday() =
        runTest(testDispatcher) {
            val vm = viewModel()
            assertEquals(AddEditRecurringUiState.Loading, vm.uiState.value)
            advanceUntilIdle()

            val state = vm.ready()

            assertFalse(vm.isEditRoute)
            assertFalse(state.isEditMode)
            assertEquals(BuiltInCategories.RECURRING, state.form.category)
            assertEquals(day(2026, 3, 15), state.form.startDate)
            assertEquals("15", state.form.dayText)
        }

    @Test
    fun newItem_archivedRecurringCategory_fallsBackToDefault() =
        runTest(testDispatcher) {
            val categories = FakeCategoryRepository(listOf(BuiltInCategories.RECURRING.copy(archived = true)))
            val vm = viewModel(categories = categories)
            advanceUntilIdle()

            assertEquals(
                BuiltInCategories.SHOPPING.id,
                vm
                    .ready()
                    .form.category
                    ?.id,
            )
        }

    @Test
    fun editRoute_loadsItemIntoForm() =
        runTest(testDispatcher) {
            val item = recurringItem(id = "r5", name = "Gym", amountCents = 2_050, note = "memo")
            val vm = viewModel(FakeRecurringRepository(listOf(item)), id = "r5")
            advanceUntilIdle()

            val state = vm.ready()

            assertTrue(vm.isEditRoute)
            assertTrue(state.isEditMode)
            assertEquals("Gym", state.form.name)
            assertEquals("memo", state.form.note)
            assertEquals(BuiltInCategories.FOOD.id, state.form.category?.id)
            assertTrue(state.form.amountText.endsWith("20,50") || state.form.amountText.endsWith("20.50"))
        }

    @Test
    fun editRoute_notFound_andLoadFailure_showError() =
        runTest(testDispatcher) {
            val missing = viewModel(FakeRecurringRepository(), id = "zzz")
            advanceUntilIdle()
            assertEquals(
                AddEditRecurringUiState.Error(UiText.StringResource(R.string.error_recurring_not_found)),
                missing.uiState.value,
            )

            val failing = viewModel(FakeRecurringRepository(nextObserveError = IllegalStateException("x")), id = "r1")
            advanceUntilIdle()
            assertEquals(
                AddEditRecurringUiState.Error(UiText.StringResource(R.string.error_generic_fallback)),
                failing.uiState.value,
            )
        }

    @Test
    fun save_invalid_setsErrors_andDoesNotWrite() =
        runTest(testDispatcher) {
            val repo = FakeRecurringRepository()
            val vm = viewModel(repo)
            advanceUntilIdle()

            vm.onSave()
            advanceUntilIdle()

            val state = vm.ready()
            assertNotNull(state.errors?.name)
            assertNotNull(state.errors?.amount)
            assertFalse(state.isSaving)
            assertTrue(repo.items.isEmpty())
        }

    @Test
    fun formChange_clearsErrors_andCapsNameAtLimit() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onSave()
            assertNotNull(vm.ready().errors)

            vm.onFormChanged(vm.ready().form.copy(name = "a".repeat(250)))

            assertNull(vm.ready().errors)
            assertEquals(
                200,
                vm
                    .ready()
                    .form.name.length,
            )
        }

    @Test
    fun customCategory_isExposedInOptions() =
        runTest(testDispatcher) {
            val custom = userCategory("pets", "Pets")
            val vm = viewModel(categories = FakeCategoryRepository(listOf(custom)))
            backgroundScope.launch { vm.categoryOptions.collect {} }
            advanceUntilIdle()

            assertTrue(vm.categoryOptions.value.any { it.id == custom.id })
        }
}
