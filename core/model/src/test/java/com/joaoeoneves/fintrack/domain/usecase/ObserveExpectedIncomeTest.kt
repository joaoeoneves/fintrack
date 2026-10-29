package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.MANAGED_MEAL
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.paydayIncome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** [ObserveExpectedIncome]: the Home "Expected income" group (payday is Fri 30 Oct 2026, window opens 23 Oct). */
class ObserveExpectedIncomeTest {
    private val oct = YearMonth.of(2026, 10)
    private val utc = ZoneOffset.UTC
    private val plan = FakePlannedIncomeRepository()
    private val monthSettings = FakeMonthSettingsRepository()
    private val incomes = FakeIncomeRepository()
    private val useCase = ObserveExpectedIncome(plan, monthSettings, incomes)

    private fun line(
        id: String,
        cents: Long = 100_000L,
        order: Int = 0,
    ) = PlannedIncome(id, "Line $id", cents, order)

    private suspend fun expected(today: LocalDate) = useCase(today, utc).first()

    /** The rows of [today]'s own month (earlier months' unreceived lines are listed separately). */
    private suspend fun rowsOf(today: LocalDate) = expected(today).entries.filter { it.month == YearMonth.from(today) }.map { it.row }

    @Test
    fun emptyPlan_hasNoRows() =
        runTest {
            val result = expected(LocalDate.of(2026, 10, 30))

            assertTrue(result.rows.isEmpty())
            assertEquals(LocalDate.of(2026, 10, 30), result.payday)
            assertEquals(oct, result.month)
        }

    @Test
    fun eightDaysBeforePayday_isHidden() =
        runTest {
            plan.savePlannedIncome(line("p1"))

            assertTrue(rowsOf(LocalDate.of(2026, 10, 22)).isEmpty())
        }

    @Test
    fun sevenDaysBeforePayday_opensTheWindow() =
        runTest {
            plan.savePlannedIncome(line("p1"))

            assertEquals(listOf("p1"), rowsOf(LocalDate.of(2026, 10, 23)).map { it.lines.single().id })
        }

    @Test
    fun staysOpenUntilTheMonthEnds() =
        runTest {
            plan.savePlannedIncome(line("p1"))

            assertEquals(1, rowsOf(LocalDate.of(2026, 10, 30)).size)
            assertEquals(1, rowsOf(LocalDate.of(2026, 10, 31)).size)
        }

    @Test
    fun weekendMonthEnd_windowIsRelativeToTheFridayPayday() =
        runTest {
            plan.savePlannedIncome(line("p1"))

            // Jan 2027: payday Fri 29, so the window opens Fri 22.
            assertTrue(rowsOf(LocalDate.of(2027, 1, 21)).isEmpty())
            assertEquals(1, rowsOf(LocalDate.of(2027, 1, 22)).size)
        }

    @Test
    fun receivedRow_isRemoved_othersStay() =
        runTest {
            val a = line("a")
            plan.savePlannedIncome(a)
            plan.savePlannedIncome(line("b", order = 1))
            incomes.addIncome(a.paydayIncome(oct, 21, LocalDate.of(2026, 10, 25), utc)!!)

            assertEquals(listOf("b"), rowsOf(LocalDate.of(2026, 10, 28)).map { it.lines.single().id })
        }

    @Test
    fun receivedOnPaydayItself_isRemoved() =
        runTest {
            val a = line("a")
            plan.savePlannedIncome(a)
            incomes.addIncome(a.paydayIncome(oct, 21, LocalDate.of(2026, 10, 30), utc)!!)

            assertTrue(rowsOf(LocalDate.of(2026, 10, 30)).isEmpty())
        }

    @Test
    fun partiallyReceivedMultiLineRow_staysListed() =
        runTest {
            val net = line("net", 200_000).copy(managedKey = MANAGED_NET_SALARY, order = 0)
            val net2 = line("net2", 50_000).copy(managedKey = MANAGED_NET_SALARY, order = 1)
            plan.savePlannedIncome(net)
            plan.savePlannedIncome(net2)
            incomes.addIncome(net.paydayIncome(oct, 21, LocalDate.of(2026, 10, 25), utc)!!)

            val rows = rowsOf(LocalDate.of(2026, 10, 28))

            assertEquals(1, rows.size)
            assertEquals(2, rows.single().lines.size)
        }

    @Test
    fun rowPayingNothingThisMonth_isNotListed() =
        runTest {
            plan.savePlannedIncome(line("zero", 0))
            plan.savePlannedIncome(line("skipped", order = 1).copy(skipMonths = setOf(10)))
            val yearly = line("yearly", order = 2).copy(frequency = IncomeFrequency.YEARLY, monthOfYear = 6)
            plan.savePlannedIncome(yearly)

            assertTrue(rowsOf(LocalDate.of(2026, 10, 28)).isEmpty())
        }

    @Test
    fun perWorkingDayRow_usesTheMonthsWorkingDays() =
        runTest {
            val meal = line("meal", 1_000).copy(basis = IncomeBasis.PER_WORKING_DAY, managedKey = MANAGED_MEAL)
            plan.savePlannedIncome(meal)
            monthSettings.setWorkingDaysOverride(oct, 18)

            val result = expected(LocalDate.of(2026, 10, 28))

            assertEquals(18, result.workingDays)
            assertEquals(18_000L, rowsOf(LocalDate.of(2026, 10, 28)).single().cents)
        }

    @Test
    fun previousMonthsReceipt_doesNotHideThisMonth() =
        runTest {
            val a = line("a")
            plan.savePlannedIncome(a)
            incomes.addIncome(a.paydayIncome(YearMonth.of(2026, 9), 22, LocalDate.of(2026, 9, 30), utc)!!)

            assertEquals(1, rowsOf(LocalDate.of(2026, 10, 28)).size)
        }

    @Test
    fun lastMonthsUnreceivedLine_staysListedAsLastMonth() =
        runTest {
            plan.savePlannedIncome(line("a"))

            val entries = expected(LocalDate.of(2026, 10, 5)).entries

            assertEquals(listOf(YearMonth.of(2026, 9)), entries.map { it.month })
            assertEquals(LocalDate.of(2026, 9, 30), entries.single().payDate)
        }

    @Test
    fun lastMonthsReceivedLine_isNotListed() =
        runTest {
            val a = line("a")
            plan.savePlannedIncome(a)
            incomes.addIncome(a.paydayIncome(YearMonth.of(2026, 9), 22, LocalDate.of(2026, 9, 30), utc)!!)

            assertTrue(expected(LocalDate.of(2026, 10, 5)).entries.isEmpty())
        }

    @Test
    fun nextMonth_isNeverListed() =
        runTest {
            plan.savePlannedIncome(line("a"))

            // 28 Oct: October's payday (30th) is in the window, November's (30 Nov) is not and must not appear.
            val months = expected(LocalDate.of(2026, 10, 28)).entries.map { it.month }

            assertTrue(YearMonth.of(2026, 11) !in months)
        }
}
