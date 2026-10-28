package com.joaoeoneves.fintrack.ui.plan

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.SuggestedGroup
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.settings.categories.MoveDirection
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CategoryGroupsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val categories = FakeCategoryRepository()
    private val groupRepo = FakeEnvelopeRepository(categories = categories)
    private val errors = mutableListOf<UiText>()
    private val saveFailed = UiText.StringResource(R.string.error_group_save_failed)
    private val names = SuggestedGroupNames("Recurring", "Investments", "Food", "Other")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start(repo: FakeEnvelopeRepository = groupRepo): CategoryGroupsViewModel {
        val viewModel = CategoryGroupsViewModel(repo, categories)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.groups.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errorEvent.collect { errors += it }
        }
        advanceUntilIdle()
        return viewModel
    }

    private fun failingGroups() =
        FakeEnvelopeRepository(
            nextWriteError = IllegalStateException("nope"),
            categories = categories,
        )

    private fun CategoryGroupsViewModel.names() = groups.value.orEmpty().map { it.name }

    @Test
    fun groups_startNull_thenEmptyList() =
        runTest(testDispatcher) {
            val viewModel = CategoryGroupsViewModel(groupRepo, categories)
            assertNull(viewModel.groups.value)

            val started = start()

            assertEquals(emptyList<Envelope>(), started.groups.value)
        }

    @Test
    fun observeError_fallsBackToEmpty() =
        runTest(testDispatcher) {
            val viewModel = start(FakeEnvelopeRepository(nextObserveEnvelopesError = IllegalStateException("x")))

            assertEquals(emptyList<Envelope>(), viewModel.groups.value)
        }

    @Test
    fun onAdd_trimsAndAppends() =
        runTest(testDispatcher) {
            val viewModel = start()

            viewModel.onAdd("  Food  ")
            viewModel.onAdd("Fun")
            advanceUntilIdle()

            assertEquals(listOf("Food", "Fun"), viewModel.names())
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onAdd_blankOrTooLong_isIgnoredWithoutWriting() =
        runTest(testDispatcher) {
            val viewModel = start()

            viewModel.onAdd("   ")
            viewModel.onAdd("")
            viewModel.onAdd("x".repeat(MAX_GROUP_NAME_LENGTH + 1))
            advanceUntilIdle()

            assertTrue(groupRepo.envelopes.isEmpty())
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onAdd_nameAtLimit_isAccepted() =
        runTest(testDispatcher) {
            val viewModel = start()

            viewModel.onAdd("x".repeat(MAX_GROUP_NAME_LENGTH))
            advanceUntilIdle()

            assertEquals(1, groupRepo.envelopes.size)
        }

    @Test
    fun onRename_trimsAndRenames_blankIgnored() =
        runTest(testDispatcher) {
            val viewModel = start()
            viewModel.onAdd("Food")
            advanceUntilIdle()
            val id = groupRepo.envelopes.single().id

            viewModel.onRename(id, "  Meals ")
            advanceUntilIdle()
            assertEquals(listOf("Meals"), viewModel.names())

            viewModel.onRename(id, "  ")
            advanceUntilIdle()
            assertEquals(listOf("Meals"), viewModel.names())
        }

    @Test
    fun onRename_unknownId_reportsError() =
        runTest(testDispatcher) {
            val viewModel = start()

            viewModel.onRename("nope", "X")
            advanceUntilIdle()

            assertEquals(listOf(saveFailed), errors)
        }

    @Test
    fun onMove_upAndDown_reorders_andEdgesAreNoOps() =
        runTest(testDispatcher) {
            val viewModel = start()
            listOf("A", "B", "C").forEach { viewModel.onAdd(it) }
            advanceUntilIdle()
            val ids = groupRepo.envelopes.map { it.id }

            viewModel.onMove(ids[2], MoveDirection.UP)
            advanceUntilIdle()
            assertEquals(listOf("A", "C", "B"), viewModel.names())

            viewModel.onMove(ids[0], MoveDirection.UP)
            viewModel.onMove(ids[1], MoveDirection.DOWN)
            viewModel.onMove("unknown", MoveDirection.DOWN)
            advanceUntilIdle()
            assertEquals(listOf("A", "C", "B"), viewModel.names())

            viewModel.onMove(ids[0], MoveDirection.DOWN)
            advanceUntilIdle()
            assertEquals(listOf("C", "A", "B"), viewModel.names())
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onDelete_removesGroup_andUngroupsItsCategories() =
        runTest(testDispatcher) {
            val viewModel = start()
            viewModel.onAdd("Food")
            advanceUntilIdle()
            val id = groupRepo.envelopes.single().id
            val gym = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING, id).getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, id)

            viewModel.onDelete(id)
            advanceUntilIdle()

            assertTrue(groupRepo.envelopes.isEmpty())
            val catalog = categories.observeCategories().first().associateBy { it.id }
            assertNull(catalog.getValue(gym).groupId)
            assertNull(catalog.getValue(BuiltInCategories.FOOD.id).groupId)
        }

    @Test
    fun writeFailure_emitsErrorEventOnce_thenRecovers() =
        runTest(testDispatcher) {
            val repo = failingGroups()
            val viewModel = start(repo)

            viewModel.onAdd("Food")
            advanceUntilIdle()
            assertEquals(listOf(saveFailed), errors)
            assertTrue(repo.envelopes.isEmpty())

            viewModel.onAdd("Food")
            advanceUntilIdle()
            assertEquals(1, repo.envelopes.size)
            assertEquals(1, errors.size)
        }

    @Test
    fun onCreateSuggested_whenEmpty_createsGroupsForActiveBuiltIns() =
        runTest(testDispatcher) {
            val viewModel = start()

            viewModel.onCreateSuggested(names)
            advanceUntilIdle()

            assertEquals(listOf("Investments", "Food", "Other"), viewModel.names())
            val idByName = groupRepo.envelopes.associate { it.name to it.id }
            val catalog = categories.observeCategories().first().associateBy { it.id }
            assertEquals(idByName["Investments"], catalog.getValue(BuiltInCategories.INVESTMENTS.id).groupId)
            assertEquals(idByName["Food"], catalog.getValue(BuiltInCategories.FOOD.id).groupId)
            assertEquals(idByName["Other"], catalog.getValue(BuiltInCategories.SHOPPING.id).groupId)
            assertEquals(idByName["Other"], catalog.getValue(BuiltInCategories.TRANSFER.id).groupId)
            assertTrue(errors.isEmpty())
        }

    @Test
    fun onCreateSuggested_whenGroupsExist_addsNothing() =
        runTest(testDispatcher) {
            val viewModel = start()
            viewModel.onAdd("Mine")
            advanceUntilIdle()

            viewModel.onCreateSuggested(names)
            advanceUntilIdle()

            assertEquals(listOf("Mine"), viewModel.names())
            assertTrue(categories.docs.isEmpty())
        }

    @Test
    fun onCreateSuggested_calledTwice_onlyTheFirstApplies() =
        runTest(testDispatcher) {
            val viewModel = start()

            viewModel.onCreateSuggested(names)
            advanceUntilIdle()
            viewModel.onCreateSuggested(names)
            advanceUntilIdle()

            assertEquals(3, groupRepo.envelopes.size) // built-in Recurring is archived, so it gets no group
        }

    @Test
    fun onCreateSuggested_groupWriteFails_reportsErrorAndFilesNothing() =
        runTest(testDispatcher) {
            val repo = failingGroups()
            val viewModel = start(repo)

            viewModel.onCreateSuggested(names)
            advanceUntilIdle()

            assertEquals(listOf(saveFailed), errors)
            assertTrue(repo.envelopes.isEmpty())
            assertTrue(categories.docs.isEmpty())
        }

    @Test
    fun onCreateSuggested_categoryWriteFails_reportsError() =
        runTest(testDispatcher) {
            val failing = FakeCategoryRepository()
            val viewModel = CategoryGroupsViewModel(groupRepo, failing)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.groups.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.errorEvent.collect { errors += it }
            }
            failing.nextWriteError = IllegalStateException("nope")

            viewModel.onCreateSuggested(names)
            advanceUntilIdle()

            assertEquals(listOf(saveFailed), errors)
        }

    @Test
    fun suggestedGroupNames_mapsEverySuggestedGroup() {
        assertEquals(
            listOf("Recurring", "Investments", "Food", "Other"),
            SuggestedGroup.entries.map { names.of(it) },
        )
    }
}
