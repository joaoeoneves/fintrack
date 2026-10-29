package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Income
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** [RecordPayday.runDue] decides "already received" by the month an income is owned by, not only by its date. */
class RecordPaydayOwnerMonthTest : RecordPaydayTestBase() {
    private val sep = YearMonth.of(2026, 9)

    @Test
    fun nextMonthsPayDatedInOctober_doesNotCountAsOctobersReceipt() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)
            val novPaidEarly = Income("pay_p1_202611", "Line p1", 100_000L, at("2026-10-29T12:00:00Z"), null)
            incomes.addIncome(novPaidEarly)

            assertEquals(1, useCase.runDue(LocalDate.of(2026, 10, 30), utc))

            assertEquals(listOf("pay_p1_202610", "pay_p1_202611"), stored().map { it.id })
            assertEquals(oct, cursor())
        }

    @Test
    fun manualIncomeOfNextMonthDatedInOctober_isAttributedByDate_andOctoberIsStillRecorded() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)
            incomes.addIncome(Income("manual", "line p1", 99_000L, at("2026-11-02T12:00:00Z"), null))

            assertEquals(1, useCase.runDue(LocalDate.of(2026, 10, 30), utc))

            assertEquals(listOf("manual", "pay_p1_202610"), stored().map { it.id })
        }

    @Test
    fun octoberPayTickedInNovember_isFoundByOwnership_soNothingIsWrittenAndTheCursorAdvances() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)
            val ticked = Income("pay_p1_202610", "Line p1", 77_000L, at("2026-11-02T12:00:00Z"), null)
            incomes.addIncome(ticked)

            assertEquals(0, useCase.runDue(LocalDate.of(2026, 11, 3), utc))

            assertEquals(listOf(ticked), stored())
            assertEquals(oct, cursor())
        }
}
