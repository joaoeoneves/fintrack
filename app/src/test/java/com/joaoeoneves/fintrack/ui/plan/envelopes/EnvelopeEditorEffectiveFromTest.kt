package com.joaoeoneves.fintrack.ui.plan.envelopes

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** The "Valid from" month of the envelope editor: default, when the row shows and what a save carries. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EnvelopeEditorEffectiveFromTest {
    private val dispatcher = StandardTestDispatcher()
    private val a = CategoryId("a")
    private val categories = FakeCategoryRepository(listOf(userCategory("a", order = 0).copy(groupId = "env")))
    private val budgets = FakeBudgetRepository(listOf(Budget(a, 100)))
    private val envelopes = FakeEnvelopeRepository(listOf(Envelope("env", "Food", 0)), categories, budgets)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.start(id: String?): EnvelopeEditorViewModel {
        val handle = SavedStateHandle(if (id != null) mapOf("envelopeId" to id) else emptyMap())
        val vm = EnvelopeEditorViewModel(handle, envelopes, categories, budgets, FakePlannedIncomeRepository())
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun EnvelopeEditorViewModel.content() = uiState.value as EnvelopeEditorUiState.Content

    @Test
    fun defaultsToTheCurrentMonth() =
        runTest(dispatcher) {
            assertEquals(YearMonth.now(), start("env").content().draft.effectiveFrom)
            assertEquals(YearMonth.now(), start(null).content().draft.effectiveFrom)
        }

    @Test
    fun existingUnchanged_hidesTheRow_changedShowsIt() =
        runTest(dispatcher) {
            val vm = start("env")
            assertFalse(vm.content().showEffectiveFrom)

            vm.onLimitChange(a, 250)
            advanceUntilIdle()
            assertTrue(vm.content().showEffectiveFrom)

            vm.onLimitChange(a, 100)
            advanceUntilIdle()
            assertFalse(vm.content().showEffectiveFrom)
        }

    @Test
    fun existing_amountChange_showsTheRow() =
        runTest(dispatcher) {
            val vm = start("env")
            vm.onModeChange(EnvelopeBudgetMode.SINGLE)
            vm.onAmountChange(5_000)
            advanceUntilIdle()
            assertTrue(vm.content().showEffectiveFrom)
        }

    @Test
    fun newEnvelope_alwaysShowsTheRow() =
        runTest(dispatcher) {
            assertTrue(start(null).content().showEffectiveFrom)
        }

    @Test
    fun save_carriesTheChosenMonth() =
        runTest(dispatcher) {
            val vm = start("env")
            val chosen = YearMonth.of(2027, 2)
            vm.onLimitChange(a, 250)
            vm.onEffectiveFromChange(chosen)
            vm.onSave()
            advanceUntilIdle()
            assertEquals(chosen, envelopes.savedEdits.single().effectiveFrom)
        }
}
