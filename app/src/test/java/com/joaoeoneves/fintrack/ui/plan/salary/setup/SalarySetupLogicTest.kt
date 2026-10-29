package com.joaoeoneves.fintrack.ui.plan.salary.setup

import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.MealPayment
import com.joaoeoneves.fintrack.domain.model.SPREAD_HALF_PPM
import com.joaoeoneves.fintrack.domain.model.SPREAD_NONE_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import com.joaoeoneves.fintrack.ui.plan.salary.toForm
import com.joaoeoneves.fintrack.ui.plan.salary.toResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalarySetupLogicTest {
    @Test
    fun privatePreset_isTodaysDefaults() {
        val form = SalaryForm(base = 100_000L, ss = "12").withSectorPreset(EmploymentSector.PRIVATE)

        assertEquals(EmploymentSector.PRIVATE, form.sector)
        assertEquals(SPREAD_HALF_PPM, form.spreadPpm)
        assertEquals(listOf(6, 12), form.subsidyMonths)
        assertEquals(MealPayment.CARD, form.mealPayment)
        assertEquals("", form.otherDeductions)
        assertEquals(100_000L, form.base)
        assertEquals("12", form.ss)
    }

    @Test
    fun publicPreset_noTwelfths_juneNovember_mealWithSalary() {
        val form = SalaryForm(otherDeductions = "3.5").withSectorPreset(EmploymentSector.PUBLIC)

        assertEquals(EmploymentSector.PUBLIC, form.sector)
        assertEquals(SPREAD_NONE_PPM, form.spreadPpm)
        assertEquals(listOf(6, 11), form.subsidyMonths)
        assertEquals(MealPayment.WITH_SALARY, form.mealPayment)
        assertEquals("", form.otherDeductions)
    }

    @Test
    fun adse_roundTrip() {
        val on = SalaryForm().withAdse(true)

        assertEquals("3.5", on.otherDeductions)
        assertTrue(on.hasAdse)
        assertFalse(on.withAdse(false).hasAdse)
        assertEquals("", on.withAdse(false).otherDeductions)
        assertTrue(SalaryForm(otherDeductions = "3,5").hasAdse)
        assertFalse(SalaryForm(otherDeductions = "4").hasAdse)
        assertFalse(SalaryForm(otherDeductions = "x").hasAdse)
    }

    @Test
    fun mealAnswer_setsRateOrPayment() {
        val base = SalaryForm(meal = 900L)

        assertEquals(0L, base.withMealAnswer(MealAnswer.NO).meal)
        assertEquals(
            MealPayment.CARD,
            base.copy(mealPayment = MealPayment.WITH_SALARY).withMealAnswer(MealAnswer.CARD).mealPayment,
        )
        val withSalary = base.withMealAnswer(MealAnswer.WITH_SALARY)
        assertEquals(MealPayment.WITH_SALARY, withSalary.mealPayment)
        assertEquals(900L, withSalary.meal)
    }

    @Test
    fun payDayDraft_validity() {
        fun fixed(text: String) = PayDayDraft(fixed = true, dayText = text)

        assertNull(fixed("0").day)
        assertEquals(1, fixed("1").day)
        assertEquals(31, fixed("31").day)
        assertNull(fixed("32").day)
        assertNull(fixed("").day)
        assertFalse(fixed("").isValid)
        assertFalse(fixed("0").isValid)
        assertFalse(fixed("32").isValid)
        assertTrue(fixed("31").isValid)
        assertTrue(PayDayDraft().isValid)
        assertNull(PayDayDraft(fixed = false, dayText = "15").day)
    }

    @Test
    fun presetsPerSector() {
        assertEquals(PayDayDraft(fixed = true), payDayPresetFor(EmploymentSector.PUBLIC))
        assertEquals(PayDayDraft(), payDayPresetFor(EmploymentSector.PRIVATE))
        assertEquals(MealAnswer.WITH_SALARY, mealAnswerPresetFor(EmploymentSector.PUBLIC))
        assertEquals(MealAnswer.CARD, mealAnswerPresetFor(EmploymentSector.PRIVATE))
    }

    @Test
    fun formRoundTrip_keepsSectorMealPaymentAndOtherDeductions() {
        val profile =
            SalaryProfile(
                baseGrossCents = 200_000L,
                sector = EmploymentSector.PUBLIC,
                mealPayment = MealPayment.WITH_SALARY,
                otherDeductionsPpm = 35_000,
                mealRatePerDayCents = 600L,
            )

        val back = profile.toForm('.').toResult().profile

        assertEquals(EmploymentSector.PUBLIC, back.sector)
        assertEquals(MealPayment.WITH_SALARY, back.mealPayment)
        assertEquals(35_000, back.otherDeductionsPpm)
        assertEquals("3.5", profile.toForm('.').otherDeductions)
    }

    @Test
    fun netMode_dropsOtherDeductions_butKeepsSectorAndMealPayment() {
        val form =
            SalaryForm(
                mode = SalaryInputMode.NET,
                netMonthly = 90_000L,
                otherDeductions = "3.5",
                sector = EmploymentSector.PUBLIC,
                mealPayment = MealPayment.WITH_SALARY,
            )

        val profile = form.toResult().profile

        assertEquals(0, profile.otherDeductionsPpm)
        assertEquals(EmploymentSector.PUBLIC, profile.sector)
        assertEquals(MealPayment.WITH_SALARY, profile.mealPayment)
        assertEquals(SPREAD_HALF_PPM, SalaryForm().spreadPpm)
    }
}
