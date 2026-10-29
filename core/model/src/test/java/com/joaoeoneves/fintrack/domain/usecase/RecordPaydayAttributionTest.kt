package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Income
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** [RecordPayday.runDue] never double-records a line that already has a manual income of the month. */
class RecordPaydayAttributionTest : RecordPaydayTestBase() {
    private val sep = YearMonth.of(2026, 9)
    private val payday = LocalDate.of(2026, 10, 30)

    @Test
    fun attributedManualIncome_isNotRecordedAgain_butTheCursorAdvances() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            plan.savePlannedIncome(line("p2", 25_000))
            cursors("p1" to sep, "p2" to sep)
            incomes.addIncome(Income("manual", "line p1", 99_000L, at("2026-10-20T12:00:00Z"), null))

            assertEquals(1, useCase.runDue(payday, utc))

            assertEquals(listOf("manual", "pay_p2_202610"), stored().map { it.id })
            assertEquals(oct, cursor("p1"))
            assertEquals(oct, cursor("p2"))
        }

    @Test
    fun withoutAManualIncome_theLineIsRecorded() =
        runTest {
            plan.savePlannedIncome(line("p1"))
            cursors("p1" to sep)

            assertEquals(1, useCase.runDue(payday, utc))

            assertEquals(listOf("pay_p1_202610"), stored().map { it.id })
        }
}
