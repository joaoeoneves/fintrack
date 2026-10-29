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
    fun source_salaryBonus_vsSalaryLabel_isNamed_labelThatIsAPrefixOfTheSourceNeverMatches() {
        val lines = listOf(PlannedIncome("s", "Salary", 1L, 0))
        assertEquals(
            IncomeSourceKey.Named("salary bonus"),
            incomeSourceOf(income("b", "Salary bonus", 1L, at(2026, 10, 1)), lines),
        )
    }

    @Test
    fun source_exactLabelWins_overLongerLabelsItIsAPrefixOf() {
        val lines = listOf(PlannedIncome("long", "Salary extra", 1L, 0), PlannedIncome("s", "Salary", 1L, 1))
        assertEquals(
            IncomeSourceKey.Row(null, null, "s"),
            incomeSourceOf(income("b", "salary", 1L, at(2026, 10, 1)), lines),
        )
    }

    @Test
    fun source_prefixOfSeveralLabels_shortestWins_tieMeansNamed() {
        val short = PlannedIncome("a", "Salary extra", 1L, 0)
        val longer = PlannedIncome("b", "Salary extra bonus", 1L, 1)
        assertEquals(
            IncomeSourceKey.Row(null, null, "a"),
            incomeSourceOf(income("x", "salary", 1L, at(2026, 10, 1)), listOf(longer, short)),
        )
        val tie = listOf(PlannedIncome("a", "Salary one", 1L, 0), PlannedIncome("b", "Salary two", 1L, 1))
        assertEquals(
            IncomeSourceKey.Named("salary"),
            incomeSourceOf(income("x", "salary", 1L, at(2026, 10, 1)), tie),
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

    private fun rentPay(
        y: Int,
        m: Int,
    ) = income("pay_rent_%d%02d".format(y, m), "Rent", 20_000L, at(y, m, 1))

    @Test
    fun history_expectedNullAfterToday_zeroNotNullBefore() {
        val months = listOf(ym(2026, 9), ym(2026, 10), ym(2026, 11))
        val paid = listOf(rentPay(2026, 9))
        val row = incomeHistoryOf(months, paid, listOf(rent), off, zone, ym(2026, 10)).single()
        assertEquals(listOf(20_000L, 20_000L, null), row.months.map { it.expectedCents })
        val skipped =
            incomeHistoryOf(months, paid, listOf(rent.copy(skipMonths = setOf(9))), off, zone, ym(2026, 10))
        assertEquals(0L, skipped.single().months[0].expectedCents)
    }

    @Test
    fun history_expectedNullBeforeTheLinesFirstPayday() {
        val months = listOf(ym(2026, 8), ym(2026, 9), ym(2026, 10))
        val row = incomeHistoryOf(months, listOf(rentPay(2026, 9)), listOf(rent), off, zone, ym(2026, 10)).single()
        assertEquals(listOf(null, 20_000L, 20_000L), row.months.map { it.expectedCents })
    }

    @Test
    fun history_rowNeverPaidViaPaydayId_hasNoExpected() {
        val manual = income("m1", "Rent", 20_000L, at(2026, 10, 1))
        val row = incomeHistoryOf(listOf(ym(2026, 10)), listOf(manual), listOf(rent), off, zone, ym(2026, 10)).single()
        assertEquals(listOf<Long?>(null), row.months.map { it.expectedCents })
        assertEquals(20_000L, row.receivedCents)
    }

    @Test
    fun firstPaydayFundingMonth_earliestFundingMonthOfTheLinesPaydays_nullWhenNone() {
        val paid = listOf(rentPay(2026, 10), rentPay(2026, 8), income("pay_other_202601", "x", 1L, at(2026, 1, 1)))
        assertEquals(ym(2026, 8), firstPaydayFundingMonth(paid, setOf("rent"), listOf(rent), off, zone))
        assertEquals(null, firstPaydayFundingMonth(paid, setOf("none"), listOf(rent), off, zone))
        assertEquals(null, firstPaydayFundingMonth(emptyList(), setOf("rent"), listOf(rent), off, zone))
    }

    @Test
    fun firstPaydayFundingMonth_usesTheShiftedFundingMonth() {
        val pay = income("pay_sal_202609", "Salário líquido", 100_000L, at(2026, 9, 30))
        assertEquals(ym(2026, 10), firstPaydayFundingMonth(listOf(pay), setOf("sal"), listOf(salary), on, zone))
        assertEquals(ym(2026, 9), firstPaydayFundingMonth(listOf(pay), setOf("sal"), listOf(salary), off, zone))
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
                listOf(income("1", "x", 0L, at(2026, 10, 5)), rentPay(2026, 10)),
                listOf(rent, rent.copy(id = "none", label = "None", amountCents = 0L, order = 3)),
                off,
                zone,
                ym(2026, 10),
            )
        // "x" received 0 and "None" expects 0 are dropped; Rent (expected, nothing received) stays.
        assertEquals(listOf("Rent"), result.map { it.label })
        assertEquals(20_000L, result.single().receivedCents)
        assertEquals(20_000L, result.single().expectedCents)
    }
}
