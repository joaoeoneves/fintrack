package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.TimeRange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** [ObservePeriodOverview] with "the salary pays for the next month": income is attributed by funding month. */
class ObservePeriodOverviewFundingTest : ObservePeriodOverviewTestBase() {
    private val august = YearMonth.of(2026, 8)
    private val salaryLine = PlannedIncome("salary", "Salário líquido", 100_000L, 0, managedKey = "salary")

    private fun received(
        id: String,
        source: String,
        cents: Long,
        month: YearMonth,
        day: Int,
    ) = Income(id = id, source = source, amountCents = cents, date = noonOn(month, day), note = null)

    @Test
    fun salaryPaidSep30_fundsOctober_notSeptember() =
        runTest {
            plannedIncome.savePlannedIncome(salaryLine)
            income.delegate.addIncome(received("sal", "Salário", 100_000L, september, 30))

            val oct = overview(Period.CurrentMonth)
            val sep = overview(Period.Month(september))

            assertEquals(100_000L, oct.summary.incomeCents)
            assertEquals(listOf("sal"), oct.monthIncome.map { it.id })
            assertEquals(0L, sep.summary.incomeCents)
            assertEquals(emptyList<Income>(), sep.monthIncome)
        }

    @Test
    fun salaryPaidInTheMonthBefore_isCountedOnce_withOtherIncomeOfTheMonth() =
        runTest {
            plannedIncome.savePlannedIncome(salaryLine)
            income.delegate.addIncome(received("sal", "Salário", 100_000L, september, 30))
            income.delegate.addIncome(received("prize", "Prémio", 7_000L, october, 5))
            // The October salary is paid at the end of October: it funds November, not October.
            income.delegate.addIncome(received("octsal", "Salário", 100_000L, october, 30))

            val oct = overview(Period.CurrentMonth)

            assertEquals(107_000L, oct.summary.incomeCents)
            assertEquals(setOf("sal", "prize"), oct.monthIncome.map { it.id }.toSet())
            assertEquals(2, oct.monthIncome.size)
        }

    @Test
    fun augustSalary_fundsSeptember_andNeverTwice() =
        runTest {
            plannedIncome.savePlannedIncome(salaryLine)
            income.delegate.addIncome(received("aug", "Salário", 90_000L, august, 28))
            income.delegate.addIncome(received("sep", "Salário", 100_000L, september, 30))

            val sep = overview(Period.Month(september))
            val oct = overview(Period.CurrentMonth)

            assertEquals(90_000L, sep.summary.incomeCents)
            assertEquals(listOf("aug"), sep.monthIncome.map { it.id })
            assertEquals(100_000L, oct.summary.incomeCents)
            assertEquals(listOf("sep"), oct.monthIncome.map { it.id })
        }

    @Test
    fun nonSalaryIncomeAtMonthEnd_staysInItsOwnMonth() =
        runTest {
            plannedIncome.savePlannedIncome(salaryLine)
            income.delegate.addIncome(received("cb", "Cashback ActivoBank", 1_500L, september, 28))

            assertEquals(1_500L, overview(Period.Month(september)).summary.incomeCents)
            assertEquals(0L, overview(Period.CurrentMonth).summary.incomeCents)
        }

    @Test
    fun noSalaryProfile_shiftIsOff_everyIncomeCountsInItsDateMonth() =
        runTest {
            plannedIncome.savePlannedIncome(PlannedIncome("man", "Salário", 100_000L, 0))
            income.delegate.addIncome(received("sal", "Salário", 100_000L, september, 30))

            assertEquals(100_000L, overview(Period.Month(september)).summary.incomeCents)
            assertEquals(0L, overview(Period.CurrentMonth).summary.incomeCents)
        }

    @Test
    fun shiftTurnedOffByThePreference_salaryCountsInItsDateMonth() =
        runTest {
            planSettings.setSalaryPaysNextMonth(false)
            plannedIncome.savePlannedIncome(salaryLine)
            income.delegate.addIncome(received("sal", "Salário", 100_000L, september, 30))

            assertEquals(100_000L, overview(Period.Month(september)).summary.incomeCents)
            assertEquals(0L, overview(Period.CurrentMonth).summary.incomeCents)
        }

    @Test
    fun customRangeStartingOnOctoberFirst_includesTheSeptemberSalary() =
        runTest {
            plannedIncome.savePlannedIncome(salaryLine)
            income.delegate.addIncome(received("sal", "Salário", 100_000L, september, 30))

            val result = overview(Period.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15)))

            assertEquals(100_000L, result.summary.incomeCents)
            assertEquals(listOf("sal"), result.monthIncome.map { it.id })
        }

    @Test
    fun rollingPeriod_countsEachShiftedSalaryOnce() =
        runTest {
            plannedIncome.savePlannedIncome(salaryLine)
            income.delegate.addIncome(received("aug", "Salário", 90_000L, august, 28))
            income.delegate.addIncome(received("sep", "Salário", 100_000L, september, 30))

            val result = overview(Period.Rolling(TimeRange.THREE_MONTHS))

            assertEquals(190_000L, result.summary.incomeCents)
        }

    @Test
    fun figures_availableIsIncomeMinusRecurringEnvelopesAndSaving() =
        runTest {
            plannedIncome.savePlannedIncome(salaryLine)
            income.delegate.addIncome(received("sal", "Salário", 100_000L, september, 30))

            val figures = overview(Period.CurrentMonth).figures

            assertEquals(100_000L, figures.incomeCents)
            assertEquals(
                figures.incomeCents - figures.recurringCents - figures.envelopesCents - figures.savingCents,
                figures.availableCents,
            )
        }

    @Test
    fun figures_withNoIncome_areZero() =
        runTest {
            val figures = overview(Period.CurrentMonth).figures

            assertEquals(0L, figures.incomeCents)
            assertEquals(0L, figures.availableCents)
        }
}
