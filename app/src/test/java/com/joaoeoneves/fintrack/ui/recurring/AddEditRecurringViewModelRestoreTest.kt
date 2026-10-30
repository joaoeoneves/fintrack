package com.joaoeoneves.fintrack.ui.recurring

import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** The recurring form's fields come back from the [SavedStateHandle] after process death. */
class AddEditRecurringViewModelRestoreTest : AddEditRecurringViewModelTestBase() {
    private fun snapshot(recurringId: String?) =
        RecurringFormSnapshot(
            recurringId = recurringId,
            startDate = "2026-02-10",
            type = "EXPENSE",
            name = "Gym",
            amountCents = 3_500L,
            categoryId = null,
            note = "monthly",
            frequency = "MONTHLY",
            dayText = "10",
            monthOfYear = null,
            endDate = "2026-12-31",
            paused = false,
        )

    private fun form(saved: String): RecurringForm {
        val vm =
            AddEditRecurringViewModel(
                FakeRecurringRepository(),
                FakeCategoryRepository(),
                clock,
                SavedStateHandle(mapOf("recurring_form" to saved)),
            )
        testDispatcher.scheduler.advanceUntilIdle()
        return vm.ready().form
    }

    private fun json(s: RecurringFormSnapshot) = Json.encodeToString(RecurringFormSnapshot.serializer(), s)

    @Test
    fun aSnapshotOfTheSameNewItem_isRestored() {
        val form = form(json(snapshot(null)))

        assertEquals("Gym", form.name)
        assertEquals(3_500L, form.amountCents)
        assertEquals("2026-02-10", form.startDate.toString())
        assertEquals("2026-12-31", form.endDate.toString())
        assertEquals("10", form.dayText)
    }

    @Test
    fun aSnapshotOfAnotherItem_isNotRestored() {
        val form = form(json(snapshot("someone-else")))

        assertEquals("", form.name)
        assertEquals(0L, form.amountCents)
    }

    @Test
    fun anUnreadableOrInvalidSnapshot_isIgnored() {
        assertEquals("", form("nope").name)
        assertEquals("", form(json(snapshot(null).copy(frequency = "SOMETIMES"))).name)
        assertEquals("", form(json(snapshot(null).copy(startDate = "garbage"))).name)
    }
}
