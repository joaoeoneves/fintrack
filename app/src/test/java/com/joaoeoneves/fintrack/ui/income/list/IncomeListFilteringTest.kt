package com.joaoeoneves.fintrack.ui.income.list

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.expense.list.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Pure-function tests for [matchesQuery] and [toListContent] on income. */
class IncomeListFilteringTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val salary =
        Income(id = "s", source = "Salary", amountCents = 250_000L, date = now, note = "October payroll")
    private val gift = Income(id = "g", source = "Gift", amountCents = 5_050L, date = now.minusSeconds(60), note = null)

    @Test
    fun matchesQuery_source_caseInsensitive() {
        assertTrue(salary.matchesQuery("SAL"))
        assertFalse(gift.matchesQuery("sal"))
    }

    @Test
    fun matchesQuery_note() {
        assertTrue(salary.matchesQuery("payroll"))
        assertFalse(gift.matchesQuery("payroll"))
    }

    @Test
    fun matchesQuery_amount_dotOrComma() {
        assertTrue(gift.matchesQuery("50.50"))
        assertTrue(gift.matchesQuery("50,5"))
        assertTrue(salary.matchesQuery("2500"))
        assertFalse(gift.matchesQuery("50.51"))
    }

    @Test
    fun toListContent_filtersByQuery_thenSorts() {
        val content = listOf(gift, salary).toListContent(Period.CurrentMonth, false, "0", SortOption.AMOUNT_ASC)

        assertEquals(listOf(gift, salary), content.income)
        assertEquals("0", content.query)
    }

    @Test
    fun toListContent_blankQuery_keepsAll_inDateDescOrder() {
        val content = listOf(gift, salary).toListContent(Period.CurrentMonth, true, " ", SortOption.DATE_DESC)

        assertEquals(listOf(salary, gift), content.income)
        assertTrue(content.canGoToNextMonth)
    }

    @Test
    fun toListContent_noMatch_isEmpty() {
        val content = listOf(gift, salary).toListContent(Period.CurrentMonth, false, "zzz", SortOption.DATE_ASC)

        assertTrue(content.income.isEmpty())
    }
}
