package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class IncomeAttributionTest {
    private val utc = ZoneOffset.UTC
    private val oct = YearMonth.of(2026, 10)
    private val at = Instant.parse("2026-10-20T10:00:00Z")
    private val salary = PlannedIncome("s", "Salário", 200_000L, 0)
    private val bonus = PlannedIncome("b", "Distr. de Lucros", 30_000L, 1)
    private val lines = listOf(salary, bonus)

    private fun income(
        id: String,
        source: String,
        cents: Long = 1_000L,
        date: Instant = at,
    ) = Income(id, source, cents, date, null)

    @Test
    fun payId_beatsLabel() {
        val i = income("pay_b_202610", "Salário")
        assertEquals(bonus, lineOfIncome(i, lines))
    }

    @Test
    fun label_isAccentCaseAndSpaceNormalised() {
        assertEquals(salary, lineOfIncome(income("x", "  SALARIO  "), lines))
        assertEquals(bonus, lineOfIncome(income("y", "distr.   de   lucros"), lines))
    }

    @Test
    fun prefixRule_wholeWordsOnly() {
        val long = PlannedIncome("l", "Salário líquido", 1L, 2)
        assertEquals(long, lineOfIncome(income("p", "Salario"), listOf(long)))
        assertNull(lineOfIncome(income("q", "Sala"), listOf(long)))
        assertNull(lineOfIncome(income("r", "Salário líquido extra"), listOf(long)))
    }

    @Test
    fun unknownSourceAndUnknownPayId_haveNoLine() {
        assertNull(lineOfIncome(income("z", "Venda"), lines))
        assertNull(lineOfIncome(income("pay_gone_202610", "Venda"), lines))
    }

    @Test
    fun attribute_dropsOtherMonthsPayId_andKeepsEachIncomeOnce() {
        val mine = income("pay_s_202610", "Salário", 5L)
        val last = income("pay_s_202609", "Salário", 6L)
        val manual = income("m1", "salario", 7L)
        val other = income("m2", "Distr. de Lucros", 8L)
        val result = attributeIncomes(oct, listOf(mine, last, manual, other, income("n", "Nada")), lines)

        assertEquals(listOf(mine, manual), result["s"])
        assertEquals(listOf(other), result["b"])
        assertEquals(3, result.values.sumOf { it.size })
        assertTrue(last !in result.values.flatten())
    }

    @Test
    fun rowReceived_withManualRecord_matchesByLabel() {
        val im = IncomeMonth.forMonth(oct, 21)
        val row = incomeRowsIn(lines, oct, im).first { it.lines.first().id == "s" }
        val manual = income("m1", "Salario", 190_000L, Instant.parse("2026-10-29T12:00:00Z"))

        assertEquals(listOf(manual), row.receivedIncomes(oct, im, listOf(manual), lines))
        assertEquals(LocalDate.of(2026, 10, 29), row.receivedOn(oct, im, listOf(manual), utc, lines))
    }

    @Test
    fun rowReceived_partialCountsAsNotReceived() {
        val a = PlannedIncome("a", "Meal", 100L, 0)
        val b = PlannedIncome("b2", "Meal two", 100L, 1)
        val row = IncomeRow(null, listOf(a, b), 200L)
        val im = IncomeMonth.forMonth(oct, 21)

        assertNull(row.receivedIncomes(oct, im, listOf(income("pay_a_202610", "Meal")), listOf(a, b)))
        assertNull(row.receivedOn(oct, im, listOf(income("pay_a_202610", "Meal")), utc, listOf(a, b)))
        val both = listOf(income("pay_a_202610", "Meal"), income("pay_b2_202610", "x"))
        assertEquals(2, row.receivedIncomes(oct, im, both, listOf(a, b))?.size)
    }

    @Test
    fun incomeSourceOf_agreesWithLineOfIncome() {
        val i = income("m", "SALARIO")
        assertEquals(salary.rowKey(), incomeSourceOf(i, lines))
        assertEquals(IncomeSourceKey.Named("venda de carro"), incomeSourceOf(income("v", "Venda  de Cárro"), lines))
        assertEquals(bonus.rowKey(), incomeSourceOf(income("pay_b_202610", "whatever"), lines))
    }
}
