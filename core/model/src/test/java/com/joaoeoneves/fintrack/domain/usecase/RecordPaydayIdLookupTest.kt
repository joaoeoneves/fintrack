package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.paydayIncomeId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** [RecordPayday.runDue] looks a payday income up by id before writing, so it never overwrites or guesses. */
class RecordPaydayIdLookupTest : RecordPaydayTestBase() {
    private val payday = LocalDate.of(2026, 10, 30)
    private val sep = YearMonth.of(2026, 9)

    @Test
    fun existingPayIncomeDatedOutsideTheMonth_isNotOverwritten_andTheCursorAdvances() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)
            val early = Income(paydayIncomeId("p1", oct), "Line p1", 42_000L, at("2026-11-03T12:00:00Z"), null)
            incomes.addIncome(early)

            val created = useCase.runDue(payday, utc)

            assertEquals(0, created)
            assertEquals(listOf(early), stored())
            assertEquals(oct, cursor())
        }

    @Test
    fun failedIdLookup_recordsNothing_andLeavesTheCursor() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)
            incomes.nextGetIncomeError = IllegalStateException("offline")

            val created = useCase.runDue(payday, utc)

            assertEquals(0, created)
            assertTrue(stored().isEmpty())
            assertEquals(sep, cursor())
        }

    @Test
    fun afterAFailedLookup_theNextRunRecordsTheMonth() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)
            incomes.nextGetIncomeError = IllegalStateException("offline")
            useCase.runDue(payday, utc)

            assertEquals(1, useCase.runDue(payday, utc))

            assertEquals(listOf(paydayIncomeId("p1", oct)), stored().map { it.id })
            assertEquals(oct, cursor())
        }

    @Test
    fun cursorWrittenWithTheIncomes_isTheMonthRecorded() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)

            useCase.runDue(payday, utc)

            assertEquals(mapOf("p1" to oct), automation.recorded.single().first)
        }
}
