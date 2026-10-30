package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.MAX_VACATION_DAYS
import org.junit.Assert.assertEquals
import org.junit.Test

class SalaryFormParsingVacationTest {
    @Test
    fun blank_isEmpty() {
        assertEquals(Parsed.Empty, parseVacationDays(""))
        assertEquals(Parsed.Empty, parseVacationDays("   "))
    }

    @Test
    fun nonNumeric_isInvalid() {
        assertEquals(Parsed.Invalid, parseVacationDays("abc"))
        assertEquals(Parsed.Invalid, parseVacationDays("22.5"))
        assertEquals(Parsed.Invalid, parseVacationDays("22,5"))
        assertEquals(Parsed.Invalid, parseVacationDays("1 2"))
    }

    @Test
    fun bounds_zeroAndMaxAreValid_outsideIsInvalid() {
        assertEquals(Parsed.Value(0), parseVacationDays("0"))
        assertEquals(Parsed.Value(MAX_VACATION_DAYS), parseVacationDays(MAX_VACATION_DAYS.toString()))
        assertEquals(Parsed.Invalid, parseVacationDays((MAX_VACATION_DAYS + 1).toString()))
        assertEquals(Parsed.Invalid, parseVacationDays("-1"))
        assertEquals(Parsed.Invalid, parseVacationDays("99999999999"))
    }

    @Test
    fun valid_isTrimmed() {
        assertEquals(Parsed.Value(22), parseVacationDays("22"))
        assertEquals(Parsed.Value(25), parseVacationDays("  25 "))
    }

    @Test
    fun toResult_carriesVacationDaysAndFlagsInvalid() {
        val ok = SalaryForm(base = 100_000L, vacationDays = "25").toResult()
        assertEquals(25, ok.vacationDays)
        assertEquals(25, ok.profile.vacationDaysPerYear)

        val blank = SalaryForm(base = 100_000L).toResult()
        assertEquals(null, blank.vacationDays)

        val bad = SalaryForm(base = 100_000L, vacationDays = "99").toResult()
        assertEquals(null, bad.vacationDays)
        assertEquals(setOf(SalaryField.VACATION_DAYS), bad.invalid)
    }
}
