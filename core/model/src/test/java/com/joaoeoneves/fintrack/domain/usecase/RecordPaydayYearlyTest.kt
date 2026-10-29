package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.MANAGED_CHRISTMAS
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** [RecordPayday.runDue] for a yearly salary-profile line: recorded in its pay month with the plan month's amount. */
class RecordPaydayYearlyTest : RecordPaydayTestBase() {
    @Test
    fun yearlyLine_isRecordedInItsPayMonth_withTheAmountOfThePlanMonth() =
        runTest {
            val xmas =
                PlannedIncome(
                    "xmas",
                    "Christmas",
                    663_30L,
                    0,
                    frequency = IncomeFrequency.YEARLY,
                    monthOfYear = 11,
                    managedKey = MANAGED_CHRISTMAS,
                    autoRecord = true,
                )
            plan.savePlannedIncome(xmas)
            cursors("xmas" to YearMonth.of(2026, 10))

            val created = useCase.runDue(LocalDate.of(2026, 12, 1), utc)

            assertEquals(1, created)
            val income = stored().single()
            assertEquals("pay_xmas_202611", income.id)
            assertEquals(663_30L, income.amountCents)
            assertEquals(LocalDate.of(2026, 11, 30), income.date.atZone(utc).toLocalDate())
        }
}
