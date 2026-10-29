package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class IncomeHistoryTest {
    private val zone = ZoneOffset.UTC
    private val off = IncomeFunding(false) { 20 }
    private val on = IncomeFunding(true) { 20 }
    private val salary = PlannedIncome("sal", "Salário líquido", 100_000L, 0, managedKey = MANAGED_NET_SALARY)
    private val rent = PlannedIncome("rent", "Rent", 20_000L, 1)

    private fun at(
        y: Int,
        m: Int,
        d: Int,
    ): Instant = LocalDate.of(y, m, d).atTime(12, 0).toInstant(zone)

    private fun ym(
        y: Int,
        m: Int,
    ) = YearMonth.of(y, m)

    private fun income(
        id: String,
        source: String,
        cents: Long,
        date: Instant,
    ) = Income(id = id, source = source, amountCents = cents, date = date, note = null)

    @Test
    fun source_paydayId_mapsToLineRow() {
        val lines = listOf(salary, rent)
        assertEquals(
            IncomeSourceKey.Row(SalaryComponent.SALARY, null, null),
            incomeSourceOf(testIncome("pay_sal_202610", 1L, at(2026, 10, 30)), lines),
        )
        assertEquals(
            IncomeSourceKey.Row(null, null, "rent"),
            incomeSourceOf(testIncome("pay_rent_202610", 1L, at(2026, 10, 30)), lines),
        )
    }

    @Test
    fun source_labelMatch_isAccentCaseInsensitive_wholeWordPrefix() {
        assertEquals(
            IncomeSourceKey.Row(SalaryComponent.SALARY, null, null),
            incomeSourceOf(income("a", "salario", 1L, at(2026, 10, 1)), listOf(salary)),
        )
    }

    @Test
    fun source_salaryBonus_vsSalaryLabel_currentlyMatchesByWholeWordPrefix() {
        // Spec said "Salary bonus" should NOT match "Salary"; the shared isWordPrefixMatch is symmetric, so it does.
        val lines = listOf(PlannedIncome("s", "Salary", 1L, 0))
        assertEquals(
            IncomeSourceKey.Row(null, null, "s"),
            incomeSourceOf(income("b", "Salary bonus", 1L, at(2026, 10, 1)), lines),
        )
    }

    @Test
    fun source_named_isTrimmedAndFolded_blankIsEmptyNamed() {
        assertEquals(
            IncomeSourceKey.Named("cafe lda"),
            incomeSourceOf(income("a", "  Café  LDA ", 1L, at(2026, 10, 1)), emptyList()),
        )
        assertEquals(IncomeSourceKey.Named(""), incomeSourceOf(income("b", "   ", 1L, at(2026, 10, 1)), listOf(rent)))
    }

    @Test
    fun history_expectedNullAfterToday_zeroNotNullBefore() {
        val months = listOf(ym(2026, 9), ym(2026, 10), ym(2026, 11))
        val row = incomeHistoryOf(months, emptyList(), listOf(rent), off, zone, ym(2026, 10)).single()
        assertEquals(listOf(20_000L, 20_000L, null), row.months.map { it.expectedCents })
        val skipped =
            incomeHistoryOf(months, emptyList(), listOf(rent.copy(skipMonths = setOf(9))), off, zone, ym(2026, 10))
        assertEquals(0L, skipped.single().months[0].expectedCents)
    }

    @Test
    fun history_shiftedSalary_countsInFundingMonth() {
        val pay = income("pay_sal_202509", "Salário líquido", 100_000L, at(2025, 9, 30))
        val months = listOf(ym(2025, 9), ym(2025, 10))
        val row = incomeHistoryOf(months, listOf(pay), listOf(salary), on, zone, ym(2025, 10)).single()
        assertEquals(listOf(0L, 100_000L), row.months.map { it.receivedCents })
    }

    @Test
    fun history_named_orderedByTotal_latestSpellingLabel() {
        val result =
            incomeHistoryOf(
                listOf(ym(2026, 9), ym(2026, 10)),
                listOf(
                    income("1", "gift", 500L, at(2026, 9, 5)),
                    income("2", "Gift ", 100L, at(2026, 10, 5)),
                    income("3", "Refund", 900L, at(2026, 10, 6)),
                ),
                emptyList(),
                off,
                zone,
                ym(2026, 10),
            )
        assertEquals(listOf("Refund", "Gift"), result.map { it.label })
        assertEquals(600L, result[1].receivedCents)
        assertTrue(result.all { it.key is IncomeSourceKey.Named })
    }

    @Test
    fun history_emptySourceDropped_rowWithExpectedKept() {
        val result =
            incomeHistoryOf(
                listOf(ym(2026, 10)),
                listOf(income("1", "x", 0L, at(2026, 10, 5))),
                listOf(rent, rent.copy(id = "none", label = "None", amountCents = 0L, order = 3)),
                off,
                zone,
                ym(2026, 10),
            )
        // "x" received 0 and "None" expects 0 are dropped; Rent (expected, nothing received) stays.
        assertEquals(listOf("Rent"), result.map { it.label })
        assertEquals(0L, result.single().receivedCents)
        assertEquals(20_000L, result.single().expectedCents)
    }
}
