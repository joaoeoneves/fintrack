package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalIncome
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncome
import com.joaoeoneves.fintrack.data.local.LocalRecurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PendingWriteTest {
    @Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
    private fun plan(
        categories: List<LocalCategory> = emptyList(),
        expenses: List<LocalExpense> = emptyList(),
        income: List<LocalIncome> = emptyList(),
        plannedIncome: List<LocalPlannedIncome> = emptyList(),
        recurring: List<LocalRecurringItem> = emptyList(),
    ) = MigrationPlan(categories, expenses, income, plannedIncome, recurring, 0)

    private fun List<List<PendingWrite>>.distinctCustomPerBatch() =
        map { batch ->
            batch
                .map { it.data["category"] ?: it.id }
                .filter { it.toString().startsWith("c") }
                .toSet()
                .size
        }

    @Test
    fun emptyPlan_hasNoBatches() {
        assertTrue(plan().toBatches().isEmpty())
    }

    @Test
    fun batchesAreOrdered_categoriesExpensesIncome() {
        val batches =
            plan(
                categories = listOf(localCategory("c1")),
                expenses = listOf(localExpense("e1")),
                income = listOf(localIncome("i1")),
            ).toBatches()

        assertEquals(listOf("categories", "expenses", "income"), batches.map { it.single().collection })
    }

    @Test
    fun noBatchExceeds400Writes() {
        val batches =
            plan(
                categories = (0 until 450).map { localCategory("c$it") },
                expenses = (0 until 850).map { localExpense("e$it") },
                income = (0 until 401).map { localIncome("i$it") },
            ).toBatches()

        assertTrue(batches.all { it.size in 1..400 })
        assertEquals(450 + 850 + 401, batches.sumOf { it.size })
        assertEquals(listOf("categories", "expenses", "income"), batches.map { it.first().collection }.distinct())
    }

    @Test
    fun expenseBatches_capDistinctCustomCategories_builtInsDontCount() {
        val expenses =
            (0 until 16).map { localExpense("e$it", "c$it") } +
                (0 until 20).map { localExpense("f$it", "FOOD") }

        val batches = plan(expenses = expenses).toBatches()

        assertEquals(listOf(15, 21), batches.map { it.size })
        assertTrue(batches.distinctCustomPerBatch().all { it <= 15 })
    }

    @Test
    fun categoryWrite_hasCategoryKeysPlusCreatedAt() {
        val write = plan(categories = listOf(localCategory("c1", "Gym", order = 7))).toBatches().single().single()

        assertEquals("categories", write.collection)
        assertEquals("c1", write.id)
        assertEquals(
            setOf(
                "name",
                "iconKey",
                "colorKey",
                "kind",
                "order",
                "archived",
                "builtIn",
                "groupId",
                "mealCardByDefault",
                "updatedAt",
                "createdAt",
            ),
            write.data.keys,
        )
        assertEquals("Gym", write.data["name"])
        assertEquals(7, write.data["order"])
        assertEquals(false, write.data["builtIn"])
        assertEquals("SPENDING", write.data["kind"])
    }

    @Test
    fun expenseWrite_hasExpectedKeysAndPlainValues() {
        val write = plan(expenses = listOf(localExpense("e1", "FOOD", 999))).toBatches().single().single()

        assertEquals("expenses", write.collection)
        assertEquals("e1", write.id)
        assertEquals(
            setOf(
                "name",
                "amountCents",
                "category",
                "date",
                "note",
                "isRefund",
                "paidWithMealCard",
                "updatedAt",
                "createdAt",
            ),
            write.data.keys,
        )
        assertEquals("Item e1", write.data["name"])
        assertEquals(999L, write.data["amountCents"])
        assertEquals("FOOD", write.data["category"])
        assertEquals(false, write.data["isRefund"])
    }

    @Test
    fun incomeWrite_hasExpectedKeysAndPlainValues() {
        val write = plan(income = listOf(localIncome("i1", 5000))).toBatches().single().single()

        assertEquals("income", write.collection)
        assertEquals("i1", write.id)
        assertEquals(setOf("source", "amountCents", "date", "note", "updatedAt", "createdAt"), write.data.keys)
        assertEquals("Salary", write.data["source"])
        assertEquals(5000L, write.data["amountCents"])
    }

    @Test
    fun unmappableRecords_throwInsteadOfBeingDropped() {
        assertThrows(IllegalStateException::class.java) {
            plan(categories = listOf(localCategory("bad", name = null))).toBatches()
        }
        assertThrows(IllegalStateException::class.java) {
            plan(expenses = listOf(localExpense("e1").copy(date = "nope"))).toBatches()
        }
        assertThrows(IllegalStateException::class.java) {
            plan(income = listOf(localIncome("i1").copy(date = "nope"))).toBatches()
        }
    }
}
