package com.joaoeoneves.fintrack.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The [IncomeHome]/[SpendingHome] routes' optional period and the add-form routes' optional period. */
class TabHomeRouteTest {
    @Test
    fun tabHomes_defaultToNoPeriod() {
        assertNull(IncomeHome().period)
        assertNull(SpendingHome().period)
        assertEquals(IncomeHome(), IncomeHome(null))
    }

    @Test
    fun tabHomes_carryTheMonthOrYearToken() {
        assertEquals("month:2026-08", IncomeHome("month:2026-08").period)
        assertEquals("year:2026", SpendingHome("year:2026").period)
        assertNotEquals(SpendingHome(), SpendingHome("year:2026"))
    }

    @Test
    fun addForms_periodIsOptionalAndIndependentOfTheId() {
        assertNull(AddEditExpense().period)
        assertNull(AddEditIncome().period)
        assertEquals("month:2026-08", AddEditExpense(period = "month:2026-08").period)
        assertNull(AddEditIncome(incomeId = "i1").period)
    }
}
