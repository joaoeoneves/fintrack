package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** The income window and the ownership of its rows follow the injected clock's zone, not the device's. */
class CombinedTransactionRepositoryZoneTest {
    private val auckland = ZoneId.of("Pacific/Auckland")
    private val clock = MutableClock(Instant.parse("2026-10-15T00:00:00Z"), auckland)

    // 1 Oct to 1 Nov in Auckland (NZDT, +13).
    private val range = InstantRange(Instant.parse("2026-09-30T11:00:00Z"), Instant.parse("2026-10-31T11:00:00Z"))

    @Test
    fun incomeWindow_isAMonthWiderOnEachSide_inTheInjectedZone() =
        runTest {
            var window: Pair<Instant, Instant>? = null
            val spy =
                object : IncomeRepository by FakeIncomeRepository() {
                    override fun observeIncome(
                        startInclusive: Instant,
                        endExclusive: Instant,
                    ): Flow<List<Income>> {
                        window = startInclusive to endExclusive
                        return FakeIncomeRepository().observeIncome(startInclusive, endExclusive)
                    }
                }

            CombinedTransactionRepository(FakeExpenseRepository(), spy, clock).observeTransactions(range).first()

            // 1 Sep 00:00 NZST (+12) and 1 Dec 00:00 NZDT (+13).
            assertEquals(Instant.parse("2026-08-31T12:00:00Z"), window?.first)
            assertEquals(Instant.parse("2026-11-30T11:00:00Z"), window?.second)
        }

    @Test
    fun incomeDatedAfterLocalMonthEnd_isOutside_evenThoughItIsStillOctoberInUtc() =
        runTest {
            val lateLocal = testIncome("late", 100L, Instant.parse("2026-10-31T20:00:00Z"))
            val earlyLocal = testIncome("early", 100L, Instant.parse("2026-09-30T12:00:00Z"))
            val income = FakeIncomeRepository(listOf(lateLocal, earlyLocal))

            val repo = CombinedTransactionRepository(FakeExpenseRepository(), income, clock)
            val tx = repo.observeTransactions(range).first()

            assertEquals(listOf("early"), tx.income.map { it.id })
        }
}
