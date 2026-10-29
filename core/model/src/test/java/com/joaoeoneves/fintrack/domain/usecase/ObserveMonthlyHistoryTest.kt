package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.repository.PlanSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class ObserveMonthlyHistoryTest {
    private val zone = ZoneOffset.UTC
    private val end = YearMonth.of(2026, 10)

    private class Transactions(
        private val expenses: List<Expense>,
        private val income: List<Income>,
    ) : TransactionRepository {
        val ranges = mutableListOf<InstantRange>()

        override fun observeTransactions(range: InstantRange): Flow<TransactionsInRange> {
            ranges += range
            return flow {
                emit(
                    TransactionsInRange(
                        range,
                        expenses.filter { it.date >= range.startInclusive && it.date < range.endExclusive },
                        income.filter { it.date >= range.startInclusive && it.date < range.endExclusive },
                    ),
                )
            }
        }
    }

    private class Shift(
        private val value: Boolean?,
    ) : PlanSettingsRepository {
        override fun observeSalaryPaysNextMonth(): Flow<Boolean?> = flowOf(value)

        override suspend fun setSalaryPaysNextMonth(enabled: Boolean) = Unit
    }

    private val salary = PlannedIncome("sal", "Salário líquido", 100_000L, 0, managedKey = MANAGED_NET_SALARY)

    private fun at(
        y: Int,
        m: Int,
        d: Int,
    ) = LocalDate.of(y, m, d).atTime(12, 0).toInstant(zone)

    private fun useCase(
        tx: TransactionRepository,
        lines: List<PlannedIncome> = emptyList(),
        shift: Boolean? = null,
    ) = ObserveMonthlyHistory(
        tx,
        PlanDataSources(
            FakeBudgetRepository(),
            FakePlannedIncomeRepository(lines),
            FakeMonthSettingsRepository(),
            FakeCategoryRepository(),
            FakeEnvelopeRepository(),
            FakeRecurringRepository(),
            Shift(shift),
        ),
    )

    @Test
    fun range_isEndMonthMinusMonths_toEndMonthInclusive() =
        runTest {
            val tx = Transactions(emptyList(), emptyList())
            val history = useCase(tx)(end, 3, zone, end).first()
            val range = tx.ranges.single()
            assertEquals(YearMonth.of(2026, 7).toInstantRange(zone).startInclusive, range.startInclusive)
            assertEquals(end.toInstantRange(zone).endExclusive, range.endExclusive)
            assertEquals(listOf(8, 9, 10), history.months.map { it.month.monthValue })
        }

    @Test
    fun endOfMonthSalary_landsInItsOwnMonth_regardlessOfShiftPref() =
        runTest {
            val pay = testIncome("pay_sal_202609", 100_000L, at(2026, 9, 30))
            listOf(null, true, false).forEach { pref ->
                val history = useCase(Transactions(emptyList(), listOf(pay)), listOf(salary), pref)
                assertEquals(listOf(100_000L, 0L), history(end, 2, zone, end).first().months.map { it.incomeCents })
            }
        }

    @Test
    fun expenseOn29October_isOctober() =
        runTest {
            val tx = Transactions(listOf(testExpense("late", 700L, at(2026, 10, 29))), emptyList())
            val history = useCase(tx)(end, 2, zone, end).first()
            assertEquals(listOf(0L, 700L), history.months.map { it.spendingCents })
        }

    @Test
    fun yearToDate_equalsSumsOfMonths() =
        runTest {
            val tx =
                Transactions(
                    listOf(testExpense("a", 300L, at(2026, 9, 3)), testExpense("b", 200L, at(2026, 10, 3))),
                    listOf(testIncome("i", 1_000L, at(2026, 10, 2))),
                )
            val history = useCase(tx)(end, 12, zone, end).first()
            assertEquals(500L, history.yearToDate.spentCents)
            assertEquals(1_000L, history.yearToDate.incomeCents)
            assertEquals(history.months.sumOf { it.spendingCents }, history.yearToDate.spentCents)
        }
}
