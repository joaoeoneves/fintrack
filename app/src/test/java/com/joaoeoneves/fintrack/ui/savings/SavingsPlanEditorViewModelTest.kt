package com.joaoeoneves.fintrack.ui.savings

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_NAME_LENGTH
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.testutil.userCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SavingsPlanEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val goals = FakeGoalRepository()
    private val recurring = FakeRecurringRepository()
    private val categories = FakeCategoryRepository()
    private val errors = mutableListOf<UiText>()
    private var backs = 0
    private val deleted = mutableListOf<DeletedSavingsPlan>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.start(id: String? = null): SavingsPlanEditorViewModel {
        val handle = SavedStateHandle(if (id != null) mapOf("id" to id) else emptyMap())
        val vm = SavingsPlanEditorViewModel(handle, goals, recurring, categories, clock)
        val unconfined = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(unconfined) { vm.uiState.collect {} }
        backgroundScope.launch(unconfined) { vm.errorEvent.collect { errors += it } }
        backgroundScope.launch(unconfined) { vm.navigateBack.collect { backs++ } }
        backgroundScope.launch(unconfined) { vm.deletedEvent.collect { deleted += it } }
        advanceUntilIdle()
        return vm
    }

    private fun SavingsPlanEditorViewModel.content() = uiState.value as SavingsPlanEditorUiState.Content

    @Test
    fun newPlan_seedsDefaults() =
        runTest(dispatcher) {
            val content = start().content()

            assertTrue(content.isNew)
            assertEquals("", content.draft.name)
            assertEquals(GoalTargetType.ANNUAL, content.draft.type)
            assertEquals(LocalDate.of(2026, 10, 15), content.draft.startDate)
            assertEquals(BuiltInCategories.INVESTMENTS.id, content.draft.categoryId)
        }

    @Test
    fun unknownId_isNotFound() =
        runTest(dispatcher) {
            assertEquals(SavingsPlanEditorUiState.NotFound, start("nope").uiState.value)
        }

    @Test
    fun existingPlan_loadsGoalAndOwnedItem() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(recurringId = "r1"))
            recurring.saveRecurring(savingsItem(amount = 7_000L, day = 12))

            val content = start("g1").content()

            assertFalse(content.isNew)
            assertEquals("Holiday", content.draft.name)
            assertEquals(7_000L, content.draft.monthlyCents)
            assertEquals("12", content.draft.dayText)
            assertEquals(120_000L, content.draft.targetCents)
        }

    @Test
    fun categories_onlyActiveSavingKinds_withFallback() =
        runTest(dispatcher) {
            categories.setDocs(
                listOf(
                    userCategory("mine", kind = CategoryKind.SAVING),
                    userCategory("old", kind = CategoryKind.SAVING, archived = true),
                    userCategory("spend", kind = CategoryKind.SPENDING),
                ),
            )

            val ids = start().content().categories.map { it.id.value }

            assertTrue("mine" in ids)
            assertTrue(BuiltInCategories.INVESTMENTS.id.value in ids)
            assertFalse("old" in ids)
            assertFalse("spend" in ids)
        }

    @Test
    fun edits_updateTheDraft() =
        runTest(dispatcher) {
            val vm = start()
            vm.onNameChange("Car")
            vm.onMonthlyChange(2_000L)
            vm.onDayChange("1a5x")
            vm.onAutoPostChange(false)
            vm.onTargetChange(9_000L)
            vm.onTypeChange(GoalTargetType.TOTAL)
            vm.onTargetDateChange(LocalDate.of(2027, 1, 1))
            vm.onInitialChange(300L)
            vm.onCategoryChange(BuiltInCategories.INVESTMENTS.id)
            advanceUntilIdle()

            val d = vm.content().draft
            assertEquals("Car", d.name)
            assertEquals(2_000L, d.monthlyCents)
            assertEquals("15", d.dayText)
            assertFalse(d.autoPost)
            assertEquals(9_000L, d.targetCents)
            assertEquals(GoalTargetType.TOTAL, d.type)
            assertEquals(LocalDate.of(2027, 1, 1), d.targetDate)
            assertEquals(300L, d.initialCents)
        }

    @Test
    fun nameIsCappedAtTheMaximum() =
        runTest(dispatcher) {
            val vm = start()
            vm.onNameChange("a".repeat(MAX_GOAL_NAME_LENGTH + 10))
            advanceUntilIdle()
            assertEquals(
                MAX_GOAL_NAME_LENGTH,
                vm
                    .content()
                    .draft.name.length,
            )
        }

    @Test
    fun saveInvalid_showsErrorsAndWritesNothing() =
        runTest(dispatcher) {
            val vm = start()

            vm.onSave()
            advanceUntilIdle()

            assertTrue(vm.content().draft.showErrors)
            assertTrue(goals.goals.isEmpty())
            assertEquals(0, backs)
        }

    @Test
    fun saveInvalidDay_isRejected() =
        runTest(dispatcher) {
            val vm = start()
            vm.onNameChange("Car")
            vm.onMonthlyChange(1_000L)
            vm.onDayChange("32")

            vm.onSave()
            advanceUntilIdle()

            assertTrue(goals.goals.isEmpty())
            assertTrue(recurring.items.isEmpty())
        }

    @Test
    fun saveNew_writesItemAndGoalAndNavigatesBack() =
        runTest(dispatcher) {
            val vm = start()
            vm.onNameChange("Car")
            vm.onMonthlyChange(1_000L)

            vm.onSave()
            advanceUntilIdle()

            val goal = goals.goals.single()
            assertEquals(recurring.items.single().id, goal.recurringId)
            assertEquals(1, backs)
            assertTrue(errors.isEmpty())
        }

    @Test
    fun saveNew_withZeroTargetAndNoMonthly_isAllowed() =
        runTest(dispatcher) {
            val vm = start()
            vm.onNameChange("Rainy day")

            vm.onSave()
            advanceUntilIdle()

            assertEquals(0L, goals.goals.single().targetCents)
            assertNull(goals.goals.single().recurringId)
            assertEquals(1, backs)
        }

    @Test
    fun saveFailure_emitsErrorAndStaysOpen() =
        runTest(dispatcher) {
            val vm = start()
            vm.onNameChange("Car")
            goals.nextSaveError = IllegalStateException("offline")

            vm.onSave()
            advanceUntilIdle()

            assertEquals(1, errors.size)
            assertEquals(0, backs)
            assertFalse(vm.content().saving)
        }

    @Test
    fun saveEdit_keepsIdAndUpdatesOwnedItem() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(recurringId = "r1"))
            recurring.saveRecurring(savingsItem())
            val vm = start("g1")
            vm.onMonthlyChange(0L)

            vm.onSave()
            advanceUntilIdle()

            assertEquals(1, goals.goals.size)
            assertFalse(recurring.items.single().active)
            assertEquals(1, backs)
        }

    @Test
    fun delete_removesItemAndGoalAndEmitsUndoPayload() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal(recurringId = "r1"))
            recurring.saveRecurring(savingsItem())
            val vm = start("g1")

            vm.onDelete()
            advanceUntilIdle()

            assertTrue(goals.goals.isEmpty())
            assertTrue(recurring.items.isEmpty())
            assertEquals("g1", deleted.single().goal.id)
            assertEquals("r1", deleted.single().item?.id)
        }

    @Test
    fun delete_onNewPlan_doesNothing() =
        runTest(dispatcher) {
            val vm = start()
            vm.onDelete()
            advanceUntilIdle()
            assertTrue(deleted.isEmpty())
            assertTrue(errors.isEmpty())
        }

    @Test
    fun deleteFailure_emitsErrorAndNoUndo() =
        runTest(dispatcher) {
            goals.saveGoal(savingsGoal())
            val vm = start("g1")
            goals.nextDeleteError = IllegalStateException("offline")

            vm.onDelete()
            advanceUntilIdle()

            assertEquals(1, errors.size)
            assertTrue(deleted.isEmpty())
            assertEquals(1, goals.goals.size)
        }
}
