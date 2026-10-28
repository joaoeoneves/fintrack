package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.paydayIncomeId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** [RecordPayday.runDue]: the automatic recording on each month's payday. */
class RecordPaydayRunDueTest : RecordPaydayTestBase() {
    private val payday = LocalDate.of(2026, 10, 30)

    @Test
    fun disabled_recordsNothing_andKeepsTheCursor() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            automation.replaceSettings(AutomationSettings(false, YearMonth.of(2026, 8)))

            assertEquals(0, useCase.runDue(payday, utc))

            assertTrue(stored().isEmpty())
            assertEquals(YearMonth.of(2026, 8), cursor())
        }

    @Test
    fun neverEnabled_nullCursor_recordsNothing() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            enabled(null)

            assertEquals(0, useCase.runDue(payday, utc))
            assertTrue(automation.recorded.isEmpty())
        }

    @Test
    fun beforePayday_recordsNothing() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            enabled(YearMonth.of(2026, 9))

            assertEquals(0, useCase.runDue(LocalDate.of(2026, 10, 29), utc))
            assertTrue(automation.recorded.isEmpty())
            assertEquals(YearMonth.of(2026, 9), cursor())
        }

    @Test
    fun onPayday_recordsEachActiveLine_datedAtPaydayNoon_andAdvancesTheCursor() =
        runTest {
            plan.savePlannedIncome(line("p1", 100_000))
            plan.savePlannedIncome(line("p2", 25_000))
            enabled(YearMonth.of(2026, 9))

            assertEquals(2, useCase.runDue(payday, utc))

            val rows = stored()
            assertEquals(listOf("pay_p1_202610", "pay_p2_202610"), rows.map { it.id })
            assertEquals(listOf(100_000L, 25_000L), rows.map { it.amountCents })
            assertTrue(rows.all { it.date == at("2026-10-30T12:00:00Z") })
            assertEquals(oct, cursor())
        }

    @Test
    fun repeatedRun_isIdempotent() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            enabled(YearMonth.of(2026, 9))

            assertEquals(1, useCase.runDue(payday, utc))
            assertEquals(0, useCase.runDue(payday, utc))

            assertEquals(1, stored().size)
        }

    @Test
    fun catchUp_isOldestFirst_andCappedToThreeMonths() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            enabled(YearMonth.of(2026, 1))

            assertEquals(3, useCase.runDue(payday, utc))

            assertEquals(
                listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), oct),
                automation.recorded.map { it.first },
            )
            assertEquals(oct, cursor())
        }

    @Test
    fun emptyPlan_doesNotAdvanceTheCursor() =
        runTest {
            enabled(YearMonth.of(2026, 9))

            assertEquals(0, useCase.runDue(payday, utc))

            assertTrue(automation.recorded.isEmpty())
            assertEquals(YearMonth.of(2026, 9), cursor())
        }

    @Test
    fun monthWithLines_butNoneActive_advancesTheCursor_withoutIncome() =
        runTest {
            plan.savePlannedIncome(line("p1").copy(skipMonths = setOf(10)))
            enabled(YearMonth.of(2026, 9))

            assertEquals(0, useCase.runDue(payday, utc))

            assertTrue(stored().isEmpty())
            assertEquals(oct, cursor())
        }

    @Test
    fun zeroAmountLine_isSkipped_othersStillRecorded() =
        runTest {
            plan.savePlannedIncome(line("p1", 0))
            plan.savePlannedIncome(line("p2", 500))
            enabled(YearMonth.of(2026, 9))

            assertEquals(1, useCase.runDue(payday, utc))
            assertEquals(listOf("pay_p2_202610"), stored().map { it.id })
        }

    @Test
    fun alreadyExistingIncome_isNotDuplicated_butTheCursorStillAdvances() =
        runTest {
            val l = line("p1")
            plan.savePlannedIncome(l)
            plan.savePlannedIncome(line("p2"))
            incomes.addIncome(
                Income(
                    paydayIncomeId("p1", oct),
                    "Line p1",
                    1L,
                    at("2026-10-20T12:00:00Z"),
                    null,
                ),
            )
            enabled(YearMonth.of(2026, 9))

            assertEquals(1, useCase.runDue(payday, utc))

            val rows = stored()
            assertEquals(listOf("pay_p1_202610", "pay_p2_202610"), rows.map { it.id })
            assertEquals(1L, rows.first().amountCents)
            assertEquals(oct, cursor())
        }

    @Test
    fun earlyReceived_suppressesTheAutomaticEntry() =
        runTest {
            val l = line("p1")
            plan.savePlannedIncome(l)
            enabled(YearMonth.of(2026, 9))
            val early = LocalDate.of(2026, 10, 20)
            assertTrue(useCase.receive(ReceiveRequest(listOf(l), oct, 21, early, early, utc)).isSuccess)

            assertEquals(0, useCase.runDue(payday, utc))

            val rows = stored()
            assertEquals(1, rows.size)
            assertEquals(at("2026-10-20T12:00:00Z"), rows.single().date)
            assertEquals(oct, cursor())
        }

    @Test
    fun perWorkingDayLine_usesTheMonthsOverride() =
        runTest {
            plan.savePlannedIncome(line("p1", 1_000).copy(basis = IncomeBasis.PER_WORKING_DAY))
            monthSettings.setWorkingDaysOverride(oct, 18)
            enabled(YearMonth.of(2026, 9))

            useCase.runDue(payday, utc)

            assertEquals(18_000L, stored().single().amountCents)
        }

    @Test
    fun perWorkingDayLine_usesCalendarDaysMinusDaysOff() =
        runTest {
            plan.savePlannedIncome(line("p1", 1_000).copy(basis = IncomeBasis.PER_WORKING_DAY))
            monthSettings.setDaysOff(oct, 3)
            enabled(YearMonth.of(2026, 9))

            useCase.runDue(payday, utc)

            // October 2026 has 21 working days (see WorkingDaysTest).
            assertEquals(18_000L, stored().single().amountCents)
            assertEquals(listOf(MonthSettings(oct, null, 3)), monthSettings.settings)
        }

    @Test
    fun failedWrite_stopsAtThatMonth_cursorStays_nextRunResumes() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            enabled(YearMonth.of(2026, 7))
            automation.nextWriteError = IllegalStateException("offline")

            assertEquals(0, useCase.runDue(payday, utc))

            assertTrue(automation.recorded.isEmpty())
            assertEquals(YearMonth.of(2026, 7), cursor())

            assertEquals(3, useCase.runDue(payday, utc))
            assertEquals(oct, cursor())
        }

    @Test
    fun failedLookup_stopsWithoutWriting() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            enabled(YearMonth.of(2026, 9))
            incomes.nextGetIncomeError = IllegalStateException("offline")

            assertEquals(0, useCase.runDue(payday, utc))

            assertTrue(automation.recorded.isEmpty())
            assertEquals(YearMonth.of(2026, 9), cursor())
        }

    @Test
    fun secondMonthFailing_keepsTheFirstMonthsProgress() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            enabled(YearMonth.of(2026, 7))
            // Succeeds for August, then fails for September.
            val flaky = FlakyAfterOne(automation)
            val flakyUseCase = RecordPayday(plan, monthSettings, incomes, flaky)

            assertEquals(1, flakyUseCase.runDue(payday, utc))

            assertEquals(YearMonth.of(2026, 8), cursor())
        }
}
