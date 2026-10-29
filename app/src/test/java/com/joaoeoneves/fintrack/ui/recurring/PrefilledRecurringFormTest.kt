package com.joaoeoneves.fintrack.ui.recurring

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The "make it recurring?" prefill: the pure form builder and how [AddEditRecurringViewModel] applies it. */
class PrefilledRecurringFormTest : AddEditRecurringViewModelTestBase() {
    private fun form(
        day: Int = 5,
        last: YearMonth = YearMonth.of(2026, 3),
        category: Category? = BuiltInCategories.FOOD,
        amount: Long = 1_000L,
    ) = prefilledRecurringForm("Netflix", amount, category, day, last)

    @Test
    fun `it starts in the month after the last one seen`() {
        assertEquals(LocalDate.of(2026, 4, 5), form(last = YearMonth.of(2026, 3)).startDate)
        assertEquals(LocalDate.of(2027, 1, 5), form(last = YearMonth.of(2026, 12)).startDate)
    }

    @Test
    fun `day 31 clamps to a short month but the day text keeps 31`() {
        val feb = form(day = 31, last = YearMonth.of(2027, 1))
        assertEquals(LocalDate.of(2027, 2, 28), feb.startDate)
        assertEquals("31", feb.dayText)
        assertEquals(LocalDate.of(2028, 2, 29), form(day = 31, last = YearMonth.of(2028, 1)).startDate)
    }

    @Test
    fun `it is a monthly expense carrying name, category and amount`() {
        val f = form()
        assertEquals("Netflix", f.name)
        assertEquals(BuiltInCategories.FOOD, f.category)
        assertEquals(1_000L, f.amountCents)
        assertEquals(RecurringFrequency.MONTHLY, f.frequency)
        assertEquals("5", f.dayText)
    }

    @Test
    fun `an out of range amount becomes 0 and a missing category stays null`() {
        assertEquals(0L, form(amount = 0L).amountCents)
        assertEquals(0L, form(amount = MAX_AMOUNT_CENTS + 1).amountCents)
        assertNull(form(category = null).category)
    }

    private fun vm(
        route: Map<String, Any>,
        categories: FakeCategoryRepository = FakeCategoryRepository(),
    ): AddEditRecurringViewModel {
        val vm = AddEditRecurringViewModel(FakeRecurringRepository(), categories, clock, SavedStateHandle(route))
        testDispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    private val prefill =
        mapOf(
            "prefillName" to "Netflix",
            "prefillAmountCents" to 1_000L,
            "prefillCategoryId" to BuiltInCategories.FOOD.id.value,
            "prefillDay" to 31,
            "prefillLastMonth" to "2026-01",
        )

    @Test
    fun `a new form applies the route prefill`() {
        val f = vm(prefill).ready().form
        assertEquals("Netflix", f.name)
        assertEquals(BuiltInCategories.FOOD.id, f.category?.id)
        assertEquals(LocalDate.of(2026, 2, 28), f.startDate)
        assertEquals("31", f.dayText)
    }

    @Test
    fun `an unknown category id leaves the category for the user to choose`() {
        val f = vm(prefill + ("prefillCategoryId" to "does-not-exist")).ready().form
        assertNull(f.category)
        assertEquals("Netflix", f.name)
    }

    @Test
    fun `an unreadable last month falls back to the current month`() {
        val f = vm(prefill + ("prefillLastMonth" to "garbage")).ready().form
        // Clock is 2026-03-15 and the day is 31: the month after March, clamped to April's 30 days.
        assertEquals(LocalDate.of(2026, 4, 30), f.startDate)
    }

    @Test
    fun `a prefill without name and amount is ignored`() {
        val f = vm(mapOf("prefillName" to "Netflix")).ready().form
        assertEquals("", f.name)
    }

    @Test
    fun `editing an existing id ignores the prefill`() {
        val repo = FakeRecurringRepository(listOf(recurringItem(id = "r1", name = "Rent")))
        val model =
            AddEditRecurringViewModel(
                repo,
                FakeCategoryRepository(),
                clock,
                SavedStateHandle(prefill + ("recurringId" to "r1")),
            )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Rent", model.ready().form.name)
    }

    @Test
    fun `a saved snapshot wins over the prefill`() {
        val snapshot =
            RecurringFormSnapshot(
                recurringId = null,
                startDate = "2026-02-10",
                type = "EXPENSE",
                name = "Typed",
                amountCents = 3_500L,
                categoryId = null,
                note = "",
                frequency = "MONTHLY",
                dayText = "10",
                monthOfYear = null,
                endDate = null,
                autoPost = true,
                paused = false,
            )
        val json = Json.encodeToString(RecurringFormSnapshot.serializer(), snapshot)
        val f = vm(prefill + ("recurring_form" to json)).ready().form
        assertEquals("Typed", f.name)
        assertTrue(f.dayText == "10")
    }
}
