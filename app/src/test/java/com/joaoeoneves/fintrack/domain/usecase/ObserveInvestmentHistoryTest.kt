package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class ObserveInvestmentHistoryTest {
    private val etf = userCategory("etf", kind = CategoryKind.SAVING)
    private val zone = ZoneOffset.UTC

    private fun inv(
        id: String,
        cents: Long,
        y: Int,
        m: Int,
        refund: Boolean = false,
    ): Expense {
        val date = LocalDate.of(y, m, 10).atTime(12, 0).toInstant(zone)
        return testExpense(id, cents, date, etf, refund).copy(name = "VWCE")
    }

    private suspend fun overview(
        end: YearMonth,
        months: Int,
        vararg items: Expense,
    ): InvestmentOverview {
        val use = ObserveInvestmentHistory(FakeExpenseRepository(items.toList()), FakeCategoryRepository(listOf(etf)))
        return use(end, months, zone).first()
    }

    @Test
    fun yearAndAllTime_goUpToTheEndMonth_independentOfTheWindow() =
        runTest {
            val o =
                overview(
                    YearMonth.of(2026, 10),
                    2,
                    inv("old", 1_000, 2025, 6),
                    inv("jan", 2_000, 2026, 1),
                    inv("sep", 3_000, 2026, 9),
                    inv("oct", 4_000, 2026, 10),
                    inv("future", 9_000, 2026, 11),
                )

            assertEquals(7_000L, o.history.totalCents)
            assertEquals(9_000L, o.yearCents)
            assertEquals(10_000L, o.allTimeCents)
        }

    @Test
    fun noData_isAllZero() =
        runTest {
            val o = overview(YearMonth.of(2026, 10), 12)

            assertEquals(0L, o.yearCents)
            assertEquals(0L, o.allTimeCents)
        }

    @Test
    fun refundNetsInTheYear() =
        runTest {
            val refund = inv("r", 1_500, 2026, 3, refund = true)
            val o = overview(YearMonth.of(2026, 10), 3, inv("a", 5_000, 2026, 3), refund)

            assertEquals(3_500L, o.yearCents)
            assertEquals(3_500L, o.allTimeCents)
        }
}
