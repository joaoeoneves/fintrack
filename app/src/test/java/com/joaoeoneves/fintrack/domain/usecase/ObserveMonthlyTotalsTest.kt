package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.CombinedTransactionRepository
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.model.MonthlyTotals
import com.joaoeoneves.fintrack.testutil.CountingExpenseRepository
import com.joaoeoneves.fintrack.testutil.CountingIncomeRepository
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMonthlyTotalsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val july = YearMonth.of(2026, 7)
    private val october = YearMonth.of(2026, 10)

    private val expenses = CountingExpenseRepository()
    private val income = CountingIncomeRepository()
    private val useCase = ObserveMonthlyTotals(CombinedTransactionRepository(expenses, income))

    private fun noon(month: YearMonth) = month.atDay(10).atTime(12, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun opensExactlyOneListenerPerSide_spanningTheWholeRange() =
        runTest {
            val totals = useCase(july, october, zone).first()

            val span = InstantRange(Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"))
            assertEquals(listOf(span), expenses.listeners.observedRanges)
            assertEquals(listOf(span), income.listeners.observedRanges)
            assertEquals(4, totals.size)
        }

    @Test
    fun fromAfterTo_emitsEmpty_withoutListeners() =
        runTest {
            assertTrue(useCase(october, july, zone).first().isEmpty())
            assertTrue(expenses.listeners.observedRanges.isEmpty())
            assertTrue(income.listeners.observedRanges.isEmpty())
        }

    @Test
    fun spanUsesTheZone() =
        runTest {
            val lisbon = ZoneId.of("Europe/Lisbon")
            useCase(july, october, lisbon).first()

            // Lisbon is on WEST (UTC+1) on 1 July but back on WET (UTC+0) by 1 November.
            val span = InstantRange(Instant.parse("2026-06-30T23:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"))
            assertEquals(listOf(span), expenses.listeners.observedRanges)
        }

    @Test
    fun liveUpdates_reEmit() =
        runTest {
            val emissions = mutableListOf<List<MonthlyTotals>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                useCase(july, october, zone).toList(emissions)
            }

            expenses.delegate.addExpense(testExpense("e", 1_500L, noon(july)))
            income.delegate.addIncome(testIncome("i", 9_000L, noon(october)))

            assertEquals(3, emissions.size)
            assertEquals(
                1_500L,
                emissions
                    .last()
                    .first()
                    .summary.spendingCents,
            )
            assertEquals(
                9_000L,
                emissions
                    .last()
                    .last()
                    .summary.incomeCents,
            )
            assertEquals(listOf(0L, 0L, 0L, 0L), emissions.first().map { it.summary.outflowCents })
            assertEquals(1, expenses.listeners.observedRanges.size)
        }
}
