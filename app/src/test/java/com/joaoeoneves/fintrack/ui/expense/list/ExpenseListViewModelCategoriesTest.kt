package com.joaoeoneves.fintrack.ui.expense.list

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExpenseListViewModelCategoriesTest {
    private val testDispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(categories: FakeCategoryRepository) =
        ExpenseListViewModel(
            FakeExpenseRepository(),
            categories,
            SavedStateHandle(mapOf("period" to "current")),
            clock,
        )

    @Test
    fun categories_initialValue_isBuiltIns() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeCategoryRepository(listOf(userCategory("gym", "Gym"))))

            assertEquals(BuiltInCategories.all, vm.categories.value)
        }

    @Test
    fun categories_afterCollection_includeUserCategories() =
        runTest(testDispatcher) {
            val gym = userCategory("gym", "Gym")
            val vm = viewModel(FakeCategoryRepository(listOf(gym)))
            backgroundScope.launch { vm.categories.collect {} }
            advanceUntilIdle()

            assertTrue(vm.categories.value.contains(gym))
        }

    @Test
    fun categories_repositoryError_fallsBackToBuiltIns() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeCategoryRepository(nextObserveCategoriesError = IllegalStateException("boom")))
            backgroundScope.launch { vm.categories.collect {} }
            advanceUntilIdle()

            assertEquals(BuiltInCategories.all, vm.categories.value)
        }
}
