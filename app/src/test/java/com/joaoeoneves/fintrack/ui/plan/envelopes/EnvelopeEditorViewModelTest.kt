package com.joaoeoneves.fintrack.ui.plan.envelopes

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.MANAGED_MEAL
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
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
import org.junit.Assert.assertNotNull
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
class EnvelopeEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val a = CategoryId("a")
    private val b = CategoryId("b")
    private val c = CategoryId("c")

    private val categories =
        FakeCategoryRepository(
            listOf(
                userCategory("a", order = 0).copy(groupId = "env"),
                userCategory("b", order = 1).copy(groupId = "other"),
                userCategory("c", order = 2),
                userCategory("d", order = 3, archived = true),
            ),
        )
    private val budgets = FakeBudgetRepository(listOf(Budget(a, 100, setOf(2))))
    private val income =
        FakePlannedIncomeRepository(
            listOf(
                PlannedIncome("l1", "Pay", 1000, 0),
                PlannedIncome("l2", "Meal", 100, 1, managedKey = MANAGED_MEAL),
                PlannedIncome("l3", "Bonus", 500, 2, frequency = IncomeFrequency.YEARLY, monthOfYear = 6),
            ),
        )
    private val envelopes =
        FakeEnvelopeRepository(
            listOf(
                Envelope("env", "Food", 0),
                Envelope("other", "Fun", 1, 3000, fundedByLineId = "l1"),
            ),
            categories,
            budgets,
        )

    private fun failing() =
        FakeEnvelopeRepository(
            envelopes.envelopes,
            categories,
            budgets,
            nextWriteError = IllegalStateException("x"),
        )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.start(
        id: String?,
        repo: FakeEnvelopeRepository = envelopes,
        events: MutableList<String> = mutableListOf(),
    ): EnvelopeEditorViewModel {
        val vm =
            EnvelopeEditorViewModel(
                SavedStateHandle(if (id != null) mapOf("envelopeId" to id) else emptyMap()),
                repo,
                categories,
                budgets,
                income,
            )
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        backgroundScope.launch(dispatcher) { vm.navigateBack.collect { events += "back" } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvent.collect { events += "error" } }
        advanceUntilIdle()
        return vm
    }

    private fun EnvelopeEditorViewModel.content() = uiState.value as EnvelopeEditorUiState.Content

    @Test
    fun existing_loadsDraftMembersAddableAndFunding() =
        runTest(dispatcher) {
            val vm = start("env")
            val content = vm.content()

            assertFalse(content.isNew)
            assertEquals("Food", content.draft.name)
            assertEquals(EnvelopeBudgetMode.PER_CATEGORY, content.draft.mode)
            assertEquals(listOf(a), content.members.map { it.category.id })
            assertEquals(100L, content.members.single().limitCents)
            val mine = content.addable.filter { it.category.id in setOf(b, c) }
            assertEquals(listOf(b, c), mine.map { it.category.id })
            assertEquals(listOf("Fun", null), mine.map { it.inEnvelopeName })
            assertTrue(content.addable.none { it.category.id.value == "d" || it.category.archived })
            assertEquals(listOf("l1", "l2"), content.fundingOptions.map { it.lineId })
            assertEquals(listOf("Fun", null), content.fundingOptions.map { it.usedByEnvelopeName })
            assertEquals(SalaryComponent.MEAL, content.fundingOptions[1].component)
        }

    @Test
    fun unknownId_isNotFound() =
        runTest(dispatcher) {
            val vm = start("missing")
            assertEquals(EnvelopeEditorUiState.NotFound, vm.uiState.value)
        }

    @Test
    fun newEnvelope_startsBlankSingle_withoutErrors() =
        runTest(dispatcher) {
            val content = start(null).content()
            assertTrue(content.isNew)
            assertEquals(EnvelopeBudgetMode.SINGLE, content.draft.mode)
            assertNull(content.nameError)
            assertNull(content.amountError)
            assertTrue(content.addable.any { it.category.id == a })
            assertTrue(content.addable.none { it.category.archived })
        }

    @Test
    fun save_newSingle_sendsEditAndNavigatesBack() =
        runTest(dispatcher) {
            val events = mutableListOf<String>()
            val vm = start(null, events = events)

            vm.onNameChange("  Travel ")
            vm.onAmountChange(4500)
            vm.onAddCategory(c)
            vm.onFundedByChange("l2")
            vm.onSave()
            advanceUntilIdle()

            val edit = envelopes.savedEdits.single()
            assertNull(edit.id)
            assertEquals("Travel", edit.name)
            assertEquals(4500L, edit.amountCents)
            assertEquals("l2", edit.fundedByLineId)
            assertEquals(setOf(c), edit.categoryIds)
            assertEquals(listOf("back"), events)
        }

    @Test
    fun save_perCategory_writesLimitsAndClearsRemoved() =
        runTest(dispatcher) {
            val vm = start("env")

            vm.onAddCategory(c)
            vm.onLimitChange(c, 250)
            vm.onLimitChange(a, null)
            vm.onSave()
            advanceUntilIdle()

            val edit = envelopes.savedEdits.single()
            assertEquals("env", edit.id)
            assertNull(edit.amountCents)
            assertEquals(mapOf<CategoryId, Budget?>(a to null, c to Budget(c, 250)), edit.limits)
        }

    @Test
    fun save_removedCategory_clearsItsLimit() =
        runTest(dispatcher) {
            val vm = start("env")

            vm.onRemoveCategory(a)
            vm.onSave()
            advanceUntilIdle()

            val edit = envelopes.savedEdits.single()
            assertTrue(edit.categoryIds.isEmpty())
            assertNull(edit.limits.getValue(a))
        }

    @Test
    fun validation_errorsOnlyAfterSave_andNoWrite() =
        runTest(dispatcher) {
            val events = mutableListOf<String>()
            val vm = start(null, events = events)
            assertNull(vm.content().nameError)

            vm.onSave()
            advanceUntilIdle()
            assertNotNull(vm.content().nameError)
            assertNull(vm.content().amountError)

            vm.onNameChange("fun")
            advanceUntilIdle()
            assertEquals(UiText.StringResource(R.string.envelope_error_name_duplicate), vm.content().nameError)
            assertNotNull(vm.content().nameError)

            vm.onNameChange("Trip")
            advanceUntilIdle()
            assertNull(vm.content().nameError)
            assertNotNull(vm.content().amountError)

            vm.onSave()
            advanceUntilIdle()
            assertTrue(envelopes.savedEdits.isEmpty())
            assertTrue(events.isEmpty())
        }

    @Test
    fun save_ownNameIsNotADuplicate() =
        runTest(dispatcher) {
            val vm = start("env")
            vm.onNameChange("food")
            vm.onSave()
            advanceUntilIdle()
            assertEquals(1, envelopes.savedEdits.size)
        }

    @Test
    fun save_failure_emitsErrorAndStays() =
        runTest(dispatcher) {
            val events = mutableListOf<String>()
            val repo = failing()
            val vm = start("env", repo, events)

            vm.onSave()
            advanceUntilIdle()

            assertEquals(listOf("error"), events)
            assertFalse(vm.content().saving)
        }

    @Test
    fun lateRepositoryEmission_doesNotOverwriteDraft() =
        runTest(dispatcher) {
            val vm = start("env")
            vm.onNameChange("Edited")
            vm.onAddCategory(c)
            advanceUntilIdle()

            envelopes.saveEnvelope(
                EnvelopeEdit(
                    "env",
                    "Remote",
                    null,
                    null,
                    emptySet(),
                    emptyMap(),
                ),
            )
            categories.setDocs(categories.docs + userCategory("e", order = 9))
            advanceUntilIdle()

            val content = vm.content()
            assertEquals("Edited", content.draft.name)
            assertEquals(listOf(a, c), content.draft.categoryIds)
            assertTrue(content.addable.any { it.category.id.value == "e" })
        }

    @Test
    fun delete_emitsSnapshotWithBudgetsAndPosition() =
        runTest(dispatcher) {
            val deleted = mutableListOf<DeletedEnvelope>()
            val vm = start("other")
            backgroundScope.launch(dispatcher) { vm.deletedEvent.collect { deleted += it } }

            vm.onDelete()
            advanceUntilIdle()

            val snapshot = deleted.single()
            assertEquals(1, snapshot.position)
            assertEquals("other", snapshot.edit.id)
            assertEquals("Fun", snapshot.edit.name)
            assertEquals(3000L, snapshot.edit.amountCents)
            assertEquals("l1", snapshot.edit.fundedByLineId)
            assertEquals(setOf(b), snapshot.edit.categoryIds)
            assertEquals(listOf("env"), envelopes.envelopes.map { it.id })
        }

    @Test
    fun delete_withBudgets_snapshotKeepsLimits() =
        runTest(dispatcher) {
            val deleted = mutableListOf<DeletedEnvelope>()
            val vm = start("env")
            backgroundScope.launch(dispatcher) { vm.deletedEvent.collect { deleted += it } }

            vm.onDelete()
            advanceUntilIdle()

            val snapshot = deleted.single()
            assertEquals(0, snapshot.position)
            assertEquals(mapOf<CategoryId, Budget?>(a to Budget(a, 100, setOf(2))), snapshot.edit.limits)
        }

    @Test
    fun delete_newEnvelope_isNoOp() =
        runTest(dispatcher) {
            val vm = start(null)
            vm.onDelete()
            advanceUntilIdle()
            assertEquals(2, envelopes.envelopes.size)
        }

    @Test
    fun delete_failure_emitsError() =
        runTest(dispatcher) {
            val events = mutableListOf<String>()
            val repo = failing()
            val vm = start("env", repo, events)
            vm.onDelete()
            advanceUntilIdle()
            assertEquals(listOf("error"), events)
        }

    @Test
    fun addCategory_twice_isIdempotent_andAmountRejectsNonPositive() =
        runTest(dispatcher) {
            val vm = start(null)
            vm.onAddCategory(c)
            vm.onAddCategory(c)
            vm.onAmountChange(-5)
            advanceUntilIdle()
            assertEquals(listOf(c), vm.content().draft.categoryIds)
            assertNull(vm.content().draft.amountCents)
        }

    @Test
    fun members_namePreviousEnvelope_onlyForCategoriesFiledElsewhere() =
        runTest(dispatcher) {
            val vm = start("env")
            vm.onAddCategory(b)
            vm.onAddCategory(c)
            advanceUntilIdle()

            val members = vm.content().members.associate { it.category.id to it.movedFromName }
            // a already belongs to this envelope, b is moved out of "Fun", c is in none.
            assertEquals(mapOf(a to null, b to "Fun", c to null), members)
        }

    @Test
    fun members_newEnvelope_hasNoMovedFromForUngroupedCategories() =
        runTest(dispatcher) {
            val vm = start(null)
            vm.onAddCategory(a)
            vm.onAddCategory(c)
            advanceUntilIdle()

            val members = vm.content().members.associate { it.category.id to it.movedFromName }
            assertEquals(mapOf(a to "Food", c to null), members)
        }

    @Test
    fun switchingAwayFromPerCategory_keepsLimits_soTheDroppedLimitsHintApplies() =
        runTest(dispatcher) {
            val vm = start("env")
            assertEquals(EnvelopeBudgetMode.PER_CATEGORY, vm.content().draft.mode)

            vm.onModeChange(EnvelopeBudgetMode.NONE)
            advanceUntilIdle()

            val draft = vm.content().draft
            val hasLimit = draft.limits.values.any { (it ?: 0L) > 0L }
            val dropsLimits = draft.mode != EnvelopeBudgetMode.PER_CATEGORY && hasLimit
            assertTrue(dropsLimits)
        }

    @Test
    fun noPositiveLimit_meansNothingIsDropped() =
        runTest(dispatcher) {
            val vm = start("env")
            vm.onLimitChange(a, null)
            vm.onModeChange(EnvelopeBudgetMode.NONE)
            advanceUntilIdle()

            val draft = vm.content().draft
            assertFalse(draft.limits.values.any { (it ?: 0L) > 0L })
        }
}
