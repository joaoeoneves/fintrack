package com.joaoeoneves.fintrack.domain.usecase

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

/** [RecordPayday.runDue]: the automatic recording on each line's pay date. */
class RecordPaydayRunDueTest : RecordPaydayTestBase() {
    private val payday = LocalDate.of(2026, 10, 30)
    private val sep = YearMonth.of(2026, 9)

    @Test
    fun linesWithoutAutoRecord_recordNothing_andGetNoCursor() =
        runTest {
            plan.savePlannedIncome(line("p1", autoRecord = false))
            cursors("p1" to YearMonth.of(2026, 8))

            assertEquals(0, useCase.runDue(payday, utc))

            assertTrue(stored().isEmpty())
            // A cursor of a line that no longer records itself is pruned.
            assertEquals(null, cursor())
        }

    @Test
    fun noCursor_getsTheInitialCursorOnly_withoutRecording() =
        runTest {
            plan.savePlannedIncome(line("p1"))

            assertEquals(0, useCase.runDue(payday, utc))

            assertTrue(stored().isEmpty())
            assertTrue(automation.recorded.isEmpty())
            assertEquals(oct, cursor())
        }

    @Test
    fun noCursor_beforePayday_startsLastMonth() =
        runTest {
            plan.savePlannedIncome(line("p1"))

            useCase.runDue(LocalDate.of(2026, 10, 29), utc)

            assertEquals(sep, cursor())
            assertTrue(stored().isEmpty())
        }

    @Test
    fun beforePayday_recordsNothing() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)

            assertEquals(0, useCase.runDue(LocalDate.of(2026, 10, 29), utc))
            assertTrue(automation.recorded.isEmpty())
            assertEquals(sep, cursor())
        }

    @Test
    fun onPayday_recordsEachActiveLine_datedAtPaydayNoon_andAdvancesTheCursors() =
        runTest {
            plan.savePlannedIncome(line("p1", 100_000))
            plan.savePlannedIncome(line("p2", 25_000))
            cursors("p1" to sep, "p2" to sep)

            assertEquals(2, useCase.runDue(payday, utc))

            val rows = stored()
            assertEquals(listOf("pay_p1_202610", "pay_p2_202610"), rows.map { it.id })
            assertEquals(listOf(100_000L, 25_000L), rows.map { it.amountCents })
            assertTrue(rows.all { it.date == at("2026-10-30T12:00:00Z") })
            assertEquals(oct, cursor("p1"))
            assertEquals(oct, cursor("p2"))
        }

    @Test
    fun onlyAutoRecordLinesAreRecorded() =
        runTest {
            plan.savePlannedIncome(line("auto"))
            plan.savePlannedIncome(line("manual", autoRecord = false))
            cursors("auto" to sep)

            assertEquals(1, useCase.runDue(payday, utc))

            assertEquals(listOf("pay_auto_202610"), stored().map { it.id })
        }

    @Test
    fun linesWithDifferentPayDays_recordIndependently_inTheSameMonth() =
        runTest {
            plan.savePlannedIncome(line("early", payDay = 5))
            plan.savePlannedIncome(line("late"))
            cursors("early" to sep, "late" to sep)

            // 10 Oct: day 5 is past, the last working day (30th) is not.
            assertEquals(1, useCase.runDue(LocalDate.of(2026, 10, 10), utc))
            assertEquals(listOf("pay_early_202610"), stored().map { it.id })
            assertEquals(at("2026-10-05T12:00:00Z"), stored().single().date)
            assertEquals(oct, cursor("early"))
            assertEquals(sep, cursor("late"))

            assertEquals(1, useCase.runDue(payday, utc))
            assertEquals(listOf("pay_early_202610", "pay_late_202610"), stored().map { it.id })
            assertEquals(oct, cursor("late"))
        }

    @Test
    fun repeatedRun_isIdempotent() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)

            assertEquals(1, useCase.runDue(payday, utc))
            assertEquals(0, useCase.runDue(payday, utc))

            assertEquals(1, stored().size)
        }

    @Test
    fun catchUp_isOldestFirst_andCappedToThreeMonths_withPerMonthCursors() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to YearMonth.of(2026, 1))

            assertEquals(3, useCase.runDue(payday, utc))

            assertEquals(
                listOf(YearMonth.of(2026, 8), sep, oct),
                automation.recorded.map { it.first["p1"] },
            )
            assertEquals(oct, cursor())
        }

    @Test
    fun emptyPlan_leavesTheCursorsAlone() =
        runTest {
            cursors("p1" to sep)

            assertEquals(0, useCase.runDue(payday, utc))

            assertTrue(automation.recorded.isEmpty())
            assertTrue(automation.cursorUpdates.isEmpty())
            assertEquals(sep, cursor())
        }

    @Test
    fun cursorsOfGoneLines_arePruned() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to oct, "gone" to sep)

            useCase.runDue(payday, utc)

            assertEquals(null, cursor("gone"))
            assertEquals(oct, cursor("p1"))
        }

    @Test
    fun monthWithLines_butNoneActive_advancesTheCursor_withoutIncome() =
        runTest {
            plan.savePlannedIncome(line("p1").copy(skipMonths = setOf(10)))
            cursors("p1" to sep)

            assertEquals(0, useCase.runDue(payday, utc))

            assertTrue(stored().isEmpty())
            assertEquals(oct, cursor())
        }

    @Test
    fun zeroAmountLine_isSkipped_othersStillRecorded() =
        runTest {
            plan.savePlannedIncome(line("p1", 0))
            plan.savePlannedIncome(line("p2", 500))
            cursors("p1" to sep, "p2" to sep)

            assertEquals(1, useCase.runDue(payday, utc))
            assertEquals(listOf("pay_p2_202610"), stored().map { it.id })
        }

    @Test
    fun existingPayIncome_isNotDuplicated_butTheCursorStillAdvances() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            plan.savePlannedIncome(line("p2"))
            incomes.addIncome(Income(paydayIncomeId("p1", oct), "Line p1", 1L, at("2026-10-20T12:00:00Z"), null))
            cursors("p1" to sep, "p2" to sep)

            assertEquals(1, useCase.runDue(payday, utc))

            val rows = stored()
            assertEquals(listOf("pay_p1_202610", "pay_p2_202610"), rows.map { it.id })
            assertEquals(1L, rows.first().amountCents)
            assertEquals(oct, cursor("p1"))
        }

    @Test
    fun earlyReceived_suppressesTheAutomaticEntry() =
        runTest {
            val l = line("p1")
            plan.savePlannedIncome(l)
            cursors("p1" to sep)
            val early = LocalDate.of(2026, 10, 20)
            assertTrue(useCase.receive(ReceiveRequest(listOf(l), oct, 21, early, early, utc)).isSuccess)

            assertEquals(0, useCase.runDue(payday, utc))

            assertEquals(at("2026-10-20T12:00:00Z"), stored().single().date)
            assertEquals(oct, cursor())
        }

    @Test
    fun perWorkingDayLine_usesTheMonthsOverride() =
        runTest {
            plan.savePlannedIncome(line("p1", 1_000).copy(basis = IncomeBasis.PER_WORKING_DAY))
            monthSettings.setWorkingDaysOverride(oct, 18)
            cursors("p1" to sep)

            useCase.runDue(payday, utc)

            assertEquals(18_000L, stored().single().amountCents)
        }

    @Test
    fun perWorkingDayLine_usesCalendarDaysMinusDaysOff() =
        runTest {
            plan.savePlannedIncome(line("p1", 1_000).copy(basis = IncomeBasis.PER_WORKING_DAY))
            monthSettings.setDaysOff(oct, 3)
            cursors("p1" to sep)

            useCase.runDue(payday, utc)

            assertEquals(18_000L, stored().single().amountCents)
            assertEquals(listOf(MonthSettings(oct, null, 3)), monthSettings.settings)
        }

    @Test
    fun failedWrite_stopsAtThatMonth_cursorStays_nextRunResumes() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to YearMonth.of(2026, 7))
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
            cursors("p1" to sep)
            incomes.nextObserveIncomeError = IllegalStateException("offline")

            assertEquals(0, useCase.runDue(payday, utc))

            assertTrue(automation.recorded.isEmpty())
            assertEquals(sep, cursor())
        }

    @Test
    fun secondMonthFailing_keepsTheFirstMonthsProgress() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to YearMonth.of(2026, 7))
            val flaky = FlakyAfterOne(automation)
            val flakyUseCase = RecordPayday(plan, monthSettings, incomes, flaky, labels)

            assertEquals(1, flakyUseCase.runDue(payday, utc))

            assertEquals(YearMonth.of(2026, 8), cursor())
        }
}
