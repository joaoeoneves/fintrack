package com.joaoeoneves.fintrack.ui.plan.envelopes

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.testutil.userCategory
import com.joaoeoneves.fintrack.ui.settings.categories.MoveDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EnvelopesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val three =
        listOf(Envelope("e1", "One", 0), Envelope("e2", "Two", 1), Envelope("e3", "Three", 2))

    private fun viewModelOf(
        envelopes: FakeEnvelopeRepository,
        categories: FakeCategoryRepository = FakeCategoryRepository(),
        budgets: FakeBudgetRepository = FakeBudgetRepository(),
    ) = EnvelopesViewModel(envelopes, categories, budgets)

    private fun order(repo: FakeEnvelopeRepository) = repo.envelopes.sortedBy { it.order }.map { it.id }

    @Test
    fun rowsOf_statuses() {
        val cats =
            listOf(
                userCategory("a").copy(groupId = "amount"),
                userCategory("b").copy(groupId = "sum"),
                userCategory("c").copy(groupId = "sum"),
                userCategory("d").copy(groupId = "track"),
            )
        val budgets = listOf(Budget(CategoryId("b"), 300), Budget(CategoryId("c"), 200), Budget(CategoryId("a"), 9))
        val rows =
            rowsOf(
                listOf(
                    Envelope("amount", "Amount", 0, 1000),
                    Envelope("sum", "Sum", 1),
                    Envelope("track", "Track", 2),
                    Envelope("empty", "Empty", 3),
                    Envelope("zero", "Zero", 4),
                ),
                cats,
                budgets,
            )
        assertEquals(EnvelopeRowStatus.Budgeted(1000), rows[0].status)
        assertEquals(EnvelopeRowStatus.Budgeted(500), rows[1].status)
        assertEquals(2, rows[1].categoryCount)
        assertEquals(EnvelopeRowStatus.Tracking, rows[2].status)
        assertEquals(EnvelopeRowStatus.Empty, rows[3].status)
        assertEquals(0, rows[3].categoryCount)
        assertEquals(listOf("Amount", "Sum", "Track", "Empty", "Zero"), rows.map { it.name })
    }

    @Test
    fun rowsOf_zeroLimitsAreTracking_andNoEnvelopesIsEmpty() {
        val rows =
            rowsOf(
                listOf(Envelope("e", "E", 0)),
                listOf(userCategory("a").copy(groupId = "e")),
                listOf(Budget(CategoryId("a"), 0)),
            )
        assertEquals(EnvelopeRowStatus.Tracking, rows.single().status)
        assertTrue(rowsOf(emptyList(), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun rowsOf_ownAmountZero_isNotBudgeted() {
        val rows = rowsOf(listOf(Envelope("e", "E", 0, 0)), emptyList(), emptyList())

        assertTrue(rows.single().status !is EnvelopeRowStatus.Budgeted)
        assertEquals(EnvelopeRowStatus.Empty, rows.single().status)
    }

    @Test
    fun rowsOf_ownAmountZeroWithCategories_isTracking() {
        val rows =
            rowsOf(
                listOf(Envelope("e", "E", 0, 0)),
                listOf(userCategory("a").copy(groupId = "e")),
                emptyList(),
            )

        assertEquals(EnvelopeRowStatus.Tracking, rows.single().status)
    }

    @Test
    fun onMove_downAndUp_reorder() =
        runTest(dispatcher) {
            val repo = FakeEnvelopeRepository(three)
            val vm = viewModelOf(repo)
            backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onMove("e1", MoveDirection.DOWN)
            advanceUntilIdle()
            assertEquals(listOf("e2", "e1", "e3"), order(repo))

            vm.onMove("e3", MoveDirection.UP)
            advanceUntilIdle()
            assertEquals(listOf("e2", "e3", "e1"), order(repo))
        }

    @Test
    fun onMove_atEdgesOrUnknown_doesNothing() =
        runTest(dispatcher) {
            val repo = FakeEnvelopeRepository(three)
            val vm = viewModelOf(repo)
            backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.onMove("e1", MoveDirection.UP)
            vm.onMove("e3", MoveDirection.DOWN)
            vm.onMove("nope", MoveDirection.DOWN)
            advanceUntilIdle()

            assertEquals(listOf("e1", "e2", "e3"), order(repo))
        }

    @Test
    fun onMove_failure_emitsError() =
        runTest(dispatcher) {
            val repo = FakeEnvelopeRepository(three, nextWriteError = IllegalStateException("x"))
            val vm = viewModelOf(repo)
            val errors = mutableListOf<Any>()
            backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvent.collect { errors += it } }
            advanceUntilIdle()

            vm.onMove("e1", MoveDirection.DOWN)
            advanceUntilIdle()

            assertEquals(1, errors.size)
        }

    @Test
    fun uiState_contentThenObserveErrorBecomesError() =
        runTest(dispatcher) {
            val ok = viewModelOf(FakeEnvelopeRepository(three))
            backgroundScope.launch(dispatcher) { ok.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(3, (ok.uiState.value as EnvelopesUiState.Content).rows.size)

            val bad = viewModelOf(FakeEnvelopeRepository(nextObserveEnvelopesError = IllegalStateException("x")))
            backgroundScope.launch(dispatcher) { bad.uiState.collect {} }
            advanceUntilIdle()
            assertTrue(bad.uiState.value is EnvelopesUiState.Error)
        }
}
