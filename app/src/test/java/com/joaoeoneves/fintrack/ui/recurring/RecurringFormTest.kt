package com.joaoeoneves.fintrack.ui.recurring

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [validateRecurringForm], [toItem], [toForm] and [newRecurringForm]. */
class RecurringFormTest {
    private val start = day(2026, 3, 1)

    private fun validForm() =
        RecurringForm(
            startDate = start,
            name = "Rent",
            amountText = "12,50",
            category = BuiltInCategories.FOOD,
            dayText = "15",
        )

    private fun msg(resId: Int) = UiText.StringResource(resId)

    @Test
    fun validForm_hasNoErrors() {
        assertNull(validateRecurringForm(validForm()))
    }

    @Test
    fun blankName_isRequired_andTooLongNameFails_atBoundary() {
        assertEquals(msg(R.string.error_name_required), validateRecurringForm(validForm().copy(name = "   "))?.name)
        assertNull(validateRecurringForm(validForm().copy(name = "a".repeat(200))))
        assertEquals(
            msg(R.string.error_recurring_too_long),
            validateRecurringForm(validForm().copy(name = "a".repeat(201)))?.name,
        )
    }

    @Test
    fun amount_mustBePositiveAndParseable() {
        for (bad in listOf("", "0", "0,00", "-5", "abc", "1,2,3")) {
            val errors = validateRecurringForm(validForm().copy(amountText = bad))
            assertEquals("amount '$bad'", msg(R.string.error_invalid_amount), errors?.amount)
        }
        assertNull(validateRecurringForm(validForm().copy(amountText = "0,01")))
    }

    @Test
    fun expenseNeedsKnownCategory_incomeDoesNot() {
        val unknown =
            Category(
                CategoryId("ghost"),
                null,
                "home",
                "teal",
                CategoryKind.SPENDING,
                1,
                archived = false,
                builtIn = false,
            )

        assertEquals(
            msg(R.string.error_category_required),
            validateRecurringForm(validForm().copy(category = null))?.category,
        )
        assertEquals(
            msg(R.string.error_category_required),
            validateRecurringForm(validForm().copy(category = unknown))?.category,
        )
        assertNull(validateRecurringForm(validForm().copy(type = RecurringType.INCOME, category = null)))
    }

    @Test
    fun dayOfMonth_boundaries() {
        for (ok in listOf("1", "31", " 15 ")) assertNull(ok, validateRecurringForm(validForm().copy(dayText = ok)))
        for (bad in listOf("0", "32", "", "x", "-1")) {
            assertEquals(
                "day '$bad'",
                msg(R.string.error_recurring_day_invalid),
                validateRecurringForm(validForm().copy(dayText = bad))?.day,
            )
        }
    }

    @Test
    fun yearlyNeedsMonthInRange_monthlyIgnoresIt() {
        val yearly = validForm().copy(frequency = RecurringFrequency.YEARLY)

        assertEquals(msg(R.string.error_recurring_month_required), validateRecurringForm(yearly)?.month)
        assertEquals(
            msg(R.string.error_recurring_month_required),
            validateRecurringForm(yearly.copy(monthOfYear = 13))?.month,
        )
        assertNull(validateRecurringForm(yearly.copy(monthOfYear = 12)))
        assertNull(validateRecurringForm(validForm().copy(monthOfYear = null)))
    }

    @Test
    fun endDateBoundary_andNoteLimit_andAccumulatedErrors() {
        assertNull(validateRecurringForm(validForm().copy(endDate = start)))
        assertEquals(
            msg(R.string.error_recurring_end_before_start),
            validateRecurringForm(validForm().copy(endDate = start.minusDays(1)))?.endDate,
        )
        assertNull(validateRecurringForm(validForm().copy(note = "😀".repeat(2000) + "   ")))
        val errors = validateRecurringForm(validForm().copy(note = "😀".repeat(2001), name = "", dayText = "0"))

        assertNotNull(errors)
        assertEquals(msg(R.string.error_recurring_too_long), errors!!.note)
        assertNotNull(errors.name)
        assertNotNull(errors.day)
        assertTrue(errors.hasAny)
    }

    @Test
    fun toItem_trimsAndDropsIrrelevantFields() {
        val monthlyIncome =
            validForm().copy(
                type = RecurringType.INCOME,
                name = "  Pay ",
                note = "   ",
                monthOfYear = 5,
                dayText = " 7 ",
            )

        val item = monthlyIncome.toItem(null)

        assertEquals("", item.id)
        assertEquals("Pay", item.name)
        assertEquals(1_250L, item.amountCents)
        assertNull(item.categoryId)
        assertNull(item.note)
        assertNull(item.monthOfYear)
        assertEquals(7, item.dayOfMonth)
        assertTrue(item.active)
        assertNull(item.handledThrough)
    }

    @Test
    fun toItem_keepsExistingIdCursorAndActive_andYearlyMonth() {
        val existing = recurringItem(id = "r9", handledThrough = day(2026, 2, 15), active = false)
        val form = validForm().copy(frequency = RecurringFrequency.YEARLY, monthOfYear = 4, note = " n ")

        val item = form.toItem(existing)

        assertEquals("r9", item.id)
        assertEquals(day(2026, 2, 15), item.handledThrough)
        assertEquals(false, item.active)
        assertEquals(4, item.monthOfYear)
        assertEquals(BuiltInCategories.FOOD.id, item.categoryId)
        assertEquals("n", item.note)
    }

    @Test
    fun toForm_roundTripsWithSeparator_andNewFormDefaultsToToday() {
        val item = recurringItem(amountCents = 1_205L, note = "memo", endDate = day(2027, 1, 1))

        val form = item.toForm(BuiltInCategories.FOOD, ',')

        assertEquals("12,05", form.amountText)
        assertEquals("15", form.dayText)
        assertEquals("memo", form.note)
        assertEquals(BuiltInCategories.FOOD, form.category)
        assertEquals(day(2027, 1, 1), form.endDate)

        val fresh = newRecurringForm(day(2026, 8, 31), BuiltInCategories.RECURRING)
        assertEquals("31", fresh.dayText)
        assertEquals(8, fresh.monthOfYear)
        assertEquals(day(2026, 8, 31), fresh.startDate)
        assertEquals(RecurringType.EXPENSE, fresh.type)
        assertEquals(BuiltInCategories.RECURRING, fresh.category)
    }
}
