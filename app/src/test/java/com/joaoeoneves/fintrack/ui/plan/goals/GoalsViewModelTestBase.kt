package com.joaoeoneves.fintrack.ui.plan.goals

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Shared fixtures for the [GoalsViewModel] tests: fake repositories, a fixed clock (2026-10-15) and a start helper. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class GoalsViewModelTestBase {
    protected class Transactions(
        var expenses: List<Expense> = emptyList(),
        var fail: Boolean = false,
    ) : TransactionRepository {
        val ranges = mutableListOf<InstantRange>()

        override fun observeTransactions(range: InstantRange): Flow<TransactionsInRange> =
            flow {
                ranges += range
                if (fail) error("boom")
                emit(
                    TransactionsInRange(
                        range,
                        expenses.filter { it.date >= range.startInclusive && it.date < range.endExclusive },
                        emptyList(),
                    ),
                )
            }
    }

    protected val dispatcher = StandardTestDispatcher()
    protected val today: LocalDate = LocalDate.of(2026, 10, 15)
    protected val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    protected val goals = FakeGoalRepository()
    protected val recurring = FakeRecurringRepository()
    protected val categories = FakeCategoryRepository()
    protected val transactions = Transactions()
    protected val errors = mutableListOf<UiText>()
    protected val investments = BuiltInCategories.INVESTMENTS.id

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    protected fun TestScope.start(
        goalRepo: FakeGoalRepository = goals,
        transactionRepo: Transactions = transactions,
    ): GoalsViewModel {
        val viewModel = GoalsViewModel(GoalsSources(goalRepo, recurring, categories, transactionRepo), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.errorEvent.collect { errors += it }
        }
        advanceUntilIdle()
        return viewModel
    }

    @Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
    protected fun goal(
        id: String = "",
        name: String = "Holiday",
        target: Long = 120_000L,
        order: Int = 0,
        archived: Boolean = false,
        type: GoalTargetType = GoalTargetType.ANNUAL,
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
        targetDate: LocalDate? = null,
        initial: Long = 0L,
        recurringIds: Set<String> = emptySet(),
    ) = SavingsGoal(
        id = id,
        name = name,
        targetCents = target,
        targetType = type,
        startDate = startDate,
        targetDate = targetDate,
        initialCents = initial,
        linkedRecurringIds = recurringIds,
        archived = archived,
        order = order,
    )
}
