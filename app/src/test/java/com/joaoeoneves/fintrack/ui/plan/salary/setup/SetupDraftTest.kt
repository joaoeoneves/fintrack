package com.joaoeoneves.fintrack.ui.plan.salary.setup

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.MealPayment
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class SetupDraftTest {
    private val full =
        SetupDraft(
            form =
                SalaryForm(
                    mode = SalaryInputMode.NET,
                    base = 250_000L,
                    spreadPpm = 500_000,
                    irsBase = "11,5",
                    irsSubsidies = "20",
                    ss = "11",
                    exempt = "50",
                    netMonthly = 190_000L,
                    netHoliday = 100_000L,
                    netChristmas = 110_000L,
                    meal = 960L,
                    subsidyMonths = listOf(6, 11),
                    sector = EmploymentSector.PUBLIC,
                    mealPayment = MealPayment.WITH_SALARY,
                    otherDeductions = "3.5",
                ),
            mealAnswer = MealAnswer.WITH_SALARY,
            payDay = PayDayDraft(fixed = true, dayText = "25", earlierIfClosed = true),
            irsJovem = true,
            effectiveFrom = YearMonth.of(2027, 2),
        )

    @Test
    fun roundTrip_restoresEverything() {
        val handle = SavedStateHandle()
        handle.saveDraft(full)
        assertEquals(full, handle.loadDraft())
    }

    @Test
    fun roundTrip_defaultsWithNullSectorEmptyMonthsAndNoEffectiveFrom() {
        val draft =
            SetupDraft(
                SalaryForm(subsidyMonths = emptyList()),
                MealAnswer.NO,
                PayDayDraft(),
                irsJovem = false,
                effectiveFrom = null,
            )
        val handle = SavedStateHandle()
        handle.saveDraft(draft)
        val loaded = handle.loadDraft()
        assertEquals(draft, loaded)
        assertNull(loaded?.form?.sector)
        assertNull(loaded?.effectiveFrom)
    }

    @Test
    fun separatorInText_isStrippedSoTheDraftStillReadsBack() {
        val handle = SavedStateHandle()
        handle.saveDraft(full.copy(form = full.form.copy(irsBase = "1|2")))
        assertEquals("12", handle.loadDraft()?.form?.irsBase)
    }

    @Test
    fun nothingStored_isNull() {
        assertNull(SavedStateHandle().loadDraft())
    }

    @Test
    fun garbage_isNull() {
        assertNull(SavedStateHandle(mapOf("draft_form" to "not a draft")).loadDraft())
        assertNull(SavedStateHandle(mapOf("draft_form" to "")).loadDraft())
    }

    @Test
    fun wrongFieldCount_isNull() {
        val handle = SavedStateHandle()
        handle.saveDraft(full)
        val stored = handle.get<String>("draft_form").orEmpty()
        assertNull(SavedStateHandle(mapOf("draft_form" to "$stored|extra")).loadDraft())
        assertNull(SavedStateHandle(mapOf("draft_form" to stored.substringBeforeLast("|"))).loadDraft())
    }

    @Test
    fun rightCountButBadValues_isNull() {
        val handle = SavedStateHandle()
        handle.saveDraft(full)
        val parts = handle.get<String>("draft_form").orEmpty().split("|")
        fun withPart(
            index: Int,
            value: String,
        ): SavedStateHandle {
            val edited = parts.toMutableList().apply { this[index] = value }
            return SavedStateHandle(mapOf("draft_form" to edited.joinToString("|")))
        }
        assertNull(withPart(0, "BOGUS").loadDraft())
        assertNull(withPart(1, "abc").loadDraft())
        assertNull(withPart(15, "MAYBE").loadDraft())
        assertNull(withPart(20, "2027-99").loadDraft())
    }
}
