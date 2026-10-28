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

            assertTrue(expected(LocalDate.of(2026, 10, 22)).rows.isEmpty())
        }

    @Test
    fun sevenDaysBeforePayday_opensTheWindow() =
        runTest {
            plan.savePlannedIncome(line("p1"))

            assertEquals(listOf("p1"), expected(LocalDate.of(2026, 10, 23)).rows.map { it.lines.single().id })
        }

    @Test
    fun staysOpenUntilTheMonthEnds() =
        runTest {
            plan.savePlannedIncome(line("p1"))

            assertEquals(1, expected(LocalDate.of(2026, 10, 30)).rows.size)
            assertEquals(1, expected(LocalDate.of(2026, 10, 31)).rows.size)
        }

    @Test
    fun weekendMonthEnd_windowIsRelativeToTheFridayPayday() =
        runTest {
            plan.savePlannedIncome(line("p1"))

            // Jan 2027: payday Fri 29, so the window opens Fri 22.
            assertTrue(useCase(LocalDate.of(2027, 1, 21), utc).first().rows.isEmpty())
            assertEquals(1, useCase(LocalDate.of(2027, 1, 22), utc).first().rows.size)
        }

    @Test
    fun receivedRow_isRemoved_othersStay() =
        runTest {
            val a = line("a")
            plan.savePlannedIncome(a)
            plan.savePlannedIncome(line("b", order = 1))
            incomes.addIncome(a.paydayIncome(oct, 21, LocalDate.of(2026, 10, 25), utc)!!)

            assertEquals(listOf("b"), expected(LocalDate.of(2026, 10, 28)).rows.map { it.lines.single().id })
        }

    @Test
    fun receivedOnPaydayItself_isRemoved() =
        runTest {
            val a = line("a")
            plan.savePlannedIncome(a)
            incomes.addIncome(a.paydayIncome(oct, 21, LocalDate.of(2026, 10, 30), utc)!!)

            assertTrue(expected(LocalDate.of(2026, 10, 30)).rows.isEmpty())
        }

    @Test
    fun partiallyReceivedMultiLineRow_staysListed() =
        runTest {
            val net = line("net", 200_000).copy(managedKey = MANAGED_NET_SALARY, order = 0)
            val net2 = line("net2", 50_000).copy(managedKey = MANAGED_NET_SALARY, order = 1)
            plan.savePlannedIncome(net)
            plan.savePlannedIncome(net2)
            incomes.addIncome(net.paydayIncome(oct, 21, LocalDate.of(2026, 10, 25), utc)!!)

            val rows = expected(LocalDate.of(2026, 10, 28)).rows

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

            assertTrue(expected(LocalDate.of(2026, 10, 28)).rows.isEmpty())
        }

    @Test
    fun perWorkingDayRow_usesTheMonthsWorkingDays() =
        runTest {
            val meal = line("meal", 1_000).copy(basis = IncomeBasis.PER_WORKING_DAY, managedKey = MANAGED_MEAL)
            plan.savePlannedIncome(meal)
            monthSettings.setWorkingDaysOverride(oct, 18)

            val result = expected(LocalDate.of(2026, 10, 28))

            assertEquals(18, result.workingDays)
            assertEquals(18_000L, result.rows.single().cents)
        }

    @Test
    fun previousMonthsReceipt_doesNotHideThisMonth() =
        runTest {
            val a = line("a")
            plan.savePlannedIncome(a)
            incomes.addIncome(a.paydayIncome(YearMonth.of(2026, 9), 22, LocalDate.of(2026, 9, 30), utc)!!)

            assertEquals(1, expected(LocalDate.of(2026, 10, 28)).rows.size)
        }
}
