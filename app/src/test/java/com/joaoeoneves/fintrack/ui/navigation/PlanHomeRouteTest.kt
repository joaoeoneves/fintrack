package com.joaoeoneves.fintrack.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The [PlanHome] route's optional month and the add-form routes' optional period. */
class PlanHomeRouteTest {
    @Test
    fun planHome_defaultsToNoMonth() {
        assertNull(PlanHome().month)
        assertEquals(PlanHome(), PlanHome(null))
    }

    @Test
    fun planHome_carriesTheMonth() {
        assertEquals("month:2026-08", PlanHome("month:2026-08").month)
        assertNotEquals(PlanHome(), PlanHome("month:2026-08"))
    }

    @Test
    fun addForms_periodIsOptionalAndIndependentOfTheId() {
        assertNull(AddEditExpense().period)
        assertNull(AddEditIncome().period)
        assertEquals("month:2026-08", AddEditExpense(period = "month:2026-08").period)
        assertNull(AddEditIncome(incomeId = "i1").period)
    }
}
