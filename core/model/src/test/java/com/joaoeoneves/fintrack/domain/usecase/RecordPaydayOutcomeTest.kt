package com.joaoeoneves.fintrack.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** [RecordPayday.runDueOutcome]: `failed` is set by a failed cursor or income write, and only then. */
class RecordPaydayOutcomeTest : RecordPaydayTestBase() {
    private val payday = LocalDate.of(2026, 10, 30)
    private val sep = YearMonth.of(2026, 9)

    @Test
    fun noLines_isNotFailed() =
        runTest {
            assertEquals(AutomationOutcome(0), useCase.runDueOutcome(payday, utc))
        }

    @Test
    fun nothingDue_isNotFailed() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)

            assertEquals(AutomationOutcome(0), useCase.runDueOutcome(LocalDate.of(2026, 10, 29), utc))
        }

    @Test
    fun recorded_isNotFailed_andCountsTheIncomes() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)

            assertEquals(AutomationOutcome(1), useCase.runDueOutcome(payday, utc))
        }

    @Test
    fun aFailedCursorWrite_isFailed_andRecordsNothing() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            automation.nextWriteError = IllegalStateException("offline")

            assertEquals(AutomationOutcome(0, failed = true), useCase.runDueOutcome(payday, utc))

            assertTrue(stored().isEmpty())
        }

    @Test
    fun aFailedIncomeWrite_isFailed_andKeepsTheCursor() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)
            automation.nextWriteError = IllegalStateException("offline")

            assertEquals(AutomationOutcome(0, failed = true), useCase.runDueOutcome(payday, utc))

            assertTrue(stored().isEmpty())
            assertEquals(sep, cursor())
        }

    @Test
    fun aLaterMonthFailing_isFailed_withTheEarlierMonthsCount() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to YearMonth.of(2026, 7))
            val flakyUseCase = RecordPayday(plan, monthSettings, incomes, FlakyAfterOne(automation), labels)

            assertEquals(AutomationOutcome(1, failed = true), flakyUseCase.runDueOutcome(payday, utc))
        }
}
