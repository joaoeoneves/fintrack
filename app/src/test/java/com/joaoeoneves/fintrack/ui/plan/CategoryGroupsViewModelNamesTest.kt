package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeCategoryGroupRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Name-uniqueness and suggested-group behavior of [CategoryGroupsViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CategoryGroupsViewModelNamesTest {
    private val testDispatcher = StandardTestDispatcher()
    private val categories = FakeCategoryRepository()
    private val groupRepo = FakeCategoryGroupRepository(categories = categories)
    private val errors = mutableListOf<UiText>()
    private val taken = UiText.StringResource(R.string.groups_name_taken)
    private val none = UiText.StringResource(R.string.groups_suggested_none)
    private val names = SuggestedGroupNames("Recurring", "Investments", "Food", "Other")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start(): CategoryGroupsViewModel {
        val viewModel = CategoryGroupsViewModel(groupRepo, categories)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.groups.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errorEvent.collect { errors += it }
        }
        advanceUntilIdle()
        return viewModel
    }

    private fun groupNames() = groupRepo.groups.map { it.name }

    @Test
    fun onAdd_duplicateName_isRejectedWithoutWrite() =
        runTest(testDispatcher) {
            val viewModel = start()
            viewModel.onAdd("Food")
            advanceUntilIdle()

            viewModel.onAdd("  fOOD ")
            advanceUntilIdle()

            assertEquals(listOf("Food"), groupNames())
            assertEquals(listOf(taken), errors)
        }

    @Test
    fun onAdd_reservedName_isRejected() =
        runTest(testDispatcher) {
            val viewModel = start()

            viewModel.onAdd("Ungrouped", reserved = listOf("ungrouped"))
            advanceUntilIdle()

            assertTrue(groupRepo.groups.isEmpty())
            assertEquals(listOf(taken), errors)
        }

    @Test
    fun onRename_toOtherGroupsName_isRejected() =
        runTest(testDispatcher) {
            val viewModel = start()
            viewModel.onAdd("A")
            viewModel.onAdd("B")
            advanceUntilIdle()

            viewModel.onRename(groupRepo.groups[0].id, "b")
            advanceUntilIdle()

            assertEquals(listOf("A", "B"), groupNames())
            assertEquals(listOf(taken), errors)
        }

    @Test
    fun onRename_caseChangeOfOwnName_isAllowed() =
        runTest(testDispatcher) {
            val viewModel = start()
            viewModel.onAdd("food")
            advanceUntilIdle()

            viewModel.onRename(groupRepo.groups.single().id, "Food")
            advanceUntilIdle()

            assertEquals(listOf("Food"), groupNames())
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onRename_toReservedName_isRejected() =
        runTest(testDispatcher) {
            val viewModel = start()
            viewModel.onAdd("A")
            advanceUntilIdle()

            viewModel.onRename(groupRepo.groups.single().id, "Ungrouped", reserved = listOf("Ungrouped"))
            advanceUntilIdle()

            assertEquals(listOf("A"), groupNames())
            assertEquals(listOf(taken), errors)
        }

    @Test
    fun onCreateSuggested_usesOnlyActiveCategories() =
        runTest(testDispatcher) {
            categories.setArchived(BuiltInCategories.FOOD.id, true)
            val viewModel = start()

            viewModel.onCreateSuggested(names)
            advanceUntilIdle()

            assertEquals(listOf("Investments", "Other"), groupNames())
            val catalog = categories.observeCategories().first().associateBy { it.id }
            assertNull(catalog.getValue(BuiltInCategories.FOOD.id).groupId)
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onCreateSuggested_noMatchingCategories_sendsNoneError() =
        runTest(testDispatcher) {
            BuiltInCategories.all.forEach { categories.setArchived(it.id, true) }
            val viewModel = start()

            viewModel.onCreateSuggested(names)
            advanceUntilIdle()

            assertTrue(groupRepo.groups.isEmpty())
            assertEquals(listOf(none), errors)
        }

    @Test
    fun onCreateSuggested_reservedName_isSkipped_andItsCategoriesStayUngrouped() =
        runTest(testDispatcher) {
            val viewModel = start()

            viewModel.onCreateSuggested(names, reserved = listOf("Food"))
            advanceUntilIdle()

            assertEquals(listOf("Investments", "Other"), groupNames())
            val catalog = categories.observeCategories().first().associateBy { it.id }
            assertNull(catalog.getValue(BuiltInCategories.FOOD.id).groupId)
        }

    @Test
    fun onCreateSuggested_duplicateNames_createdOnce() =
        runTest(testDispatcher) {
            val viewModel = start()

            viewModel.onCreateSuggested(SuggestedGroupNames("Rec", "Same", "same", "Other"))
            advanceUntilIdle()

            assertEquals(listOf("Same", "Other"), groupNames())
        }

    @Test
    fun onCreateSuggested_whenGroupsExist_isNoOp() =
        runTest(testDispatcher) {
            val viewModel = start()
            viewModel.onAdd("Mine")
            advanceUntilIdle()

            viewModel.onCreateSuggested(names)
            advanceUntilIdle()

            assertEquals(listOf("Mine"), groupNames())
        }
}
