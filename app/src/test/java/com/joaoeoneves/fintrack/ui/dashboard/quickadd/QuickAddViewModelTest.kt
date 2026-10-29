package com.joaoeoneves.fintrack.ui.dashboard.quickadd

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.QuickAddSuggestion
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.testutil.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QuickAddViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-29T10:00:00Z")
    private val clock = MutableClock(now, ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun coffee(
        id: String,
        iso: String,
    ) = Expense(id, "Coffee", 150L, BuiltInCategories.FOOD, Instant.parse(iso), null)

    private fun TestScope.started(repo: ExpenseRepository): QuickAddViewModel {
        val vm = QuickAddViewModel(repo, clock)
        backgroundScope.launch { vm.suggestions.collect {} }
        runCurrent()
        return vm
    }

    @Test
    fun `a repeated expense becomes a chip`() =
        runTest(dispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(coffee("a", "2026-10-01T09:00:00Z"), coffee("b", "2026-10-20T09:00:00Z")),
                )
            assertEquals(
                listOf(QuickAddSuggestion("Coffee", 150L, BuiltInCategories.FOOD.id, 2)),
                started(repo).suggestions.value,
            )
        }

    @Test
    fun `the observed window is the last 90 days up to tomorrow`() =
        runTest(dispatcher) {
            var from: Instant? = null
            var to: Instant? = null
            val spy =
                object : ExpenseRepository by FakeExpenseRepository() {
                    override fun observeExpenses(
                        from0: Instant,
                        to0: Instant,
                    ): Flow<List<Expense>> {
                        from = from0
                        to = to0
                        return flow { emit(emptyList()) }
                    }
                }
            started(spy)

            assertEquals(Instant.parse("2026-07-31T00:00:00Z"), from)
            assertEquals(Instant.parse("2026-10-30T10:00:00Z"), to)
        }

    @Test
    fun `a failing flow shows an empty list`() =
        runTest(dispatcher) {
            val failing =
                object : ExpenseRepository by FakeExpenseRepository() {
                    override fun observeExpenses(
                        from: Instant,
                        to: Instant,
                    ): Flow<List<Expense>> = flow { error("boom") }
                }
            assertEquals(emptyList<QuickAddSuggestion>(), started(failing).suggestions.value)
        }
}
