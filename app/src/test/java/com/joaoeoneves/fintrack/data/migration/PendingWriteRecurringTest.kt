package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalRecurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PendingWriteRecurringTest {
    private fun plan(
        recurring: List<LocalRecurringItem>,
        planned: Boolean = false,
        expense: Boolean = false,
    ) = MigrationPlan(
        emptyList(),
        emptyList(),
        if (expense) listOf(localExpense("e1")) else emptyList(),
        emptyList(),
        if (planned) listOf(localPlanned("p1")) else emptyList(),
        recurring,
        0,
    )

    @Test
    fun recurring_isBatchedLast() {
        val batches = plan(listOf(localRecurring("r1")), planned = true, expense = true).toBatches()

        assertEquals(listOf("expenses", "plannedIncome", "recurring"), batches.map { it.single().collection })
    }

    @Test
    fun recurringWrite_hasExpectedKeysAndValues() {
        val write = plan(listOf(localRecurring("r1", "FOOD"))).toBatches().single().single()

        assertEquals("recurring", write.collection)
        assertEquals("r1", write.id)
        val keys = setOf("type", "name", "amountCents", "category", "note", "frequency", "dayOfMonth", "monthOfYear")
        val dates = setOf("startDate", "endDate", "handledThrough", "active", "autoPost", "skipped", "pausedAt", "updatedAt")
        assertEquals(keys + dates, write.data.keys)
        assertEquals("EXPENSE", write.data["type"])
        assertEquals("FOOD", write.data["category"])
        assertEquals(5000L, write.data["amountCents"])
        assertEquals("2026-01-01", write.data["startDate"])
    }

    @Test
    fun guestSkippedAndPausedAt_reachTheFirestoreMap() {
        val item = localRecurring("r1", "FOOD").copy(skipped = listOf("2026-02-15"), pausedAt = "2026-03-01")

        val write = plan(listOf(item)).toBatches().single().single()

        assertEquals(listOf("2026-02-15"), write.data["skipped"])
        assertEquals("2026-03-01", write.data["pausedAt"])
    }

    @Test
    fun incomeRecurring_hasNullCategory() {
        val write = plan(listOf(localRecurring("r1", null, "INCOME"))).toBatches().single().single()

        assertEquals(null, write.data["category"])
    }

    @Test
    fun recurringBatches_capDistinctCustomCategories_builtInsDontCount() {
        val items =
            (0 until 16).map { localRecurring("c$it", "cat$it") } + (0 until 20).map { localRecurring("f$it", "FOOD") }

        val batches = plan(items).toBatches()

        assertEquals(listOf(15, 21), batches.map { it.size })
        assertTrue(
            batches.all { b ->
                b
                    .map { it.data["category"] }
                    .filter { it != "FOOD" }
                    .toSet()
                    .size <= 15
            },
        )
    }

    @Test
    fun recurringBatches_capAt400() {
        val batches = plan((0 until 401).map { localRecurring("r$it") }).toBatches()

        assertEquals(listOf(400, 1), batches.map { it.size })
    }

    @Test
    fun unmappableRecurring_throwsInsteadOfBeingDropped() {
        val broken = localRecurring("r1").copy(frequency = "WEEKLY")

        val result = runCatching { plan(listOf(broken)).toBatches() }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
