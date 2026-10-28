package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ObserveAnnualPlanTest {
    private class RecordingTransactions(
        private val expenses: List<Expense> = emptyList(),
        private val error: Throwable? = null,
    ) : TransactionRepository {
        val ranges = mutableListOf<InstantRange>()

        override fun observeTransactions(range: InstantRange): Flow<TransactionsInRange> {
            ranges += range
            return flow {
                error?.let { throw it }
                emit(
                    TransactionsInRange(
                        range,
                        expenses.filter { it.date >= range.startInclusive && it.date < range.endExclusive },
                        emptyList(),
                    ),
                )
            }
        }
    }

    private val food = Envelope("food", "Food", 0)
    private val dinners =
        Category(CategoryId("dinners"), "dinners", "i", "slate", CategoryKind.SPENDING, 0, false, false, "food")

    private fun useCase(
        transactions: TransactionRepository,
        lines: List<PlannedIncome> = emptyList(),
        goalRepository: FakeGoalRepository = FakeGoalRepository(),
        recurring: List<RecurringItem> = emptyList(),
    ) = ObserveAnnualPlan(
        transactions,
        PlanDataSources(
            FakeBudgetRepository(listOf(Budget(dinners.id, 1_000L))),
            FakePlannedIncomeRepository(lines),
            FakeMonthSettingsRepository(),
            FakeCategoryRepository(listOf(dinners)),
            FakeEnvelopeRepository(listOf(food)),
            FakeRecurringRepository(recurring),
        ),
        goalRepository,
    )

    @Test
    fun goals_areCombinedIn_archivedExcluded_plannedFromLinkedRecurring() =
        runTest {
            val item = recurringItem("m", amountCents = 1_000, categoryId = dinners.id)
            val start = LocalDate.of(2026, 1, 1)
            val goals =
                FakeGoalRepository(
                    listOf(
                        SavingsGoal("a", "A", 5_000, GoalTargetType.ANNUAL, start, linkedRecurringIds = setOf("m")),
                        SavingsGoal("b", "B", 5_000, GoalTargetType.ANNUAL, start, archived = true),
                    ),
                )

            val observe = useCase(RecordingTransactions(), goalRepository = goals, recurring = listOf(item))
            val plan = observe(2026, ZoneOffset.UTC).first()

            assertEquals(listOf("a"), plan.goals.map { it.goal.id })
            assertEquals(12_000L, plan.goals.single().plannedCents)
        }

    @Test
    fun goalsFailure_propagates() =
        runTest {
            val goals = FakeGoalRepository(nextObserveError = IllegalStateException("boom"))

            val observe = useCase(RecordingTransactions(), goalRepository = goals)

            val result = runCatching { observe(2026, ZoneOffset.UTC).first() }

            assertEquals("boom", result.exceptionOrNull()?.message)
        }

    @Test
    fun requestsExactlyTheCalendarYear_halfOpen() =
        runTest {
            val transactions = RecordingTransactions()

            useCase(transactions)(2026, ZoneOffset.UTC).first()

            assertEquals(
                InstantRange(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z")),
                transactions.ranges.single(),
            )
        }

    @Test
    fun yearBoundaryUsesTheGivenZone() =
        runTest {
            val transactions = RecordingTransactions()
            val zone = ZoneId.of("Europe/Lisbon")

            useCase(transactions)(2026, zone).first()

            assertEquals(Instant.parse("2026-01-01T00:00:00Z"), transactions.ranges.single().startInclusive)
            val tokyo = ZoneId.of("Asia/Tokyo")
            useCase(transactions)(2026, tokyo).first()
            assertEquals(Instant.parse("2025-12-31T15:00:00Z"), transactions.ranges.last().startInclusive)
        }

    @Test
    fun excludesTransactionsOutsideTheYear_andFeedsPlanAndActuals() =
        runTest {
            val inYear = testExpense("in", 700L, Instant.parse("2026-12-31T23:59:59Z"), dinners)
            val before = testExpense("before", 900L, Instant.parse("2025-12-31T23:59:59Z"), dinners)
            val next = testExpense("next", 500L, Instant.parse("2027-01-01T00:00:00Z"), dinners)

            val plan = useCase(RecordingTransactions(listOf(inYear, before, next)))(2026, ZoneOffset.UTC).first()

            assertEquals(2026, plan.year)
            assertEquals(12_000L, plan.plannedCents)
            assertEquals(700L, plan.envelopes.single().actualCents)
        }

    @Test
    fun transactionError_propagates() =
        runTest {
            val failing = RecordingTransactions(error = IllegalStateException("boom"))

            val thrown =
                assertThrows(IllegalStateException::class.java) {
                    kotlinx.coroutines.runBlocking { useCase(failing)(2026, ZoneOffset.UTC).first() }
                }

            assertEquals("boom", thrown.message)
        }
}
