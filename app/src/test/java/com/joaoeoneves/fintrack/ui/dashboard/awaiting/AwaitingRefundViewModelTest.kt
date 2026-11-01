package com.joaoeoneves.fintrack.ui.dashboard.awaiting

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AwaitingRefundViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A fake whose awaiting-refund and linked-refund streams are driven by the test. */
    private class Repo : ExpenseRepository by FakeExpenseRepository() {
        val awaiting = MutableStateFlow<List<Expense>>(emptyList())
        val linked = MutableStateFlow<List<Expense>>(emptyList())

        override fun observeAwaitingRefund(): Flow<List<Expense>> = awaiting

        override fun observeLinkedRefunds(): Flow<List<Expense>> = linked
    }

    private fun awaiting(
        id: String,
        cents: Long = 5_000L,
        day: Int = 10,
    ) = testExpense(id, cents, Instant.parse("2026-10-%02dT12:00:00Z".format(day))).copy(awaitingRefund = true)

    private fun refundOf(
        id: String,
        cents: Long,
    ) = testExpense("r-$id", cents, Instant.parse("2026-10-20T12:00:00Z")).copy(isRefund = true, refundOf = id)

    private fun TestScope.vm(repo: Repo): AwaitingRefundViewModel {
        val model = AwaitingRefundViewModel(repo)
        backgroundScope.launch { model.uiState.collect {} }
        runCurrent()
        return model
    }

    private fun AwaitingRefundViewModel.visible() = uiState.value as AwaitingRefundUiState.Visible

    @Test
    fun `hidden when nothing is awaiting`() =
        runTest(dispatcher) {
            assertEquals(AwaitingRefundUiState.Hidden, vm(Repo()).uiState.value)
        }

    @Test
    fun `total is what is left across all rows, partial refunds subtracted`() =
        runTest(dispatcher) {
            val repo = Repo()
            repo.awaiting.value = listOf(awaiting("a", 5_000), awaiting("b", 3_000))
            repo.linked.value = listOf(refundOf("a", 2_000))

            val state = vm(repo).visible()

            assertEquals(6_000L, state.totalRemainingCents)
            assertEquals(2, state.total)
        }

    @Test
    fun `a fully refunded expense leaves and the card hides once nothing is owed`() =
        runTest(dispatcher) {
            val repo = Repo()
            repo.awaiting.value = listOf(awaiting("a", 5_000))
            val model = vm(repo)
            repo.linked.value = listOf(refundOf("a", 2_000))
            runCurrent()
            assertEquals(3_000L, model.visible().totalRemainingCents)

            repo.linked.value = listOf(refundOf("a", 2_000), refundOf("a", 3_000))
            runCurrent()

            assertEquals(AwaitingRefundUiState.Hidden, model.uiState.value)
        }

    @Test
    fun `rows are newest first`() =
        runTest(dispatcher) {
            val repo = Repo()
            repo.awaiting.value = listOf(awaiting("old", day = 2), awaiting("new", day = 20), awaiting("mid", day = 9))

            assertEquals(listOf("new", "mid", "old"), vm(repo).visible().items.map { it.expense.id })
        }

    @Test
    fun `lists at most five rows but counts and totals all of them`() =
        runTest(dispatcher) {
            val repo = Repo()
            repo.awaiting.value = (1..7).map { awaiting("e$it", 1_000, day = it) }

            val state = vm(repo).visible()

            assertEquals(AWAITING_REFUND_LIMIT, state.items.size)
            assertEquals(listOf("e7", "e6", "e5", "e4", "e3"), state.items.map { it.expense.id })
            assertEquals(7, state.total)
            assertEquals(7_000L, state.totalRemainingCents)
        }
}
