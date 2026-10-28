package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPlaceholdersTest {
    private fun run(
        categories: List<com.joaoeoneves.fintrack.data.local.LocalCategory> = emptyList(),
        expenses: List<com.joaoeoneves.fintrack.data.local.LocalExpense> = emptyList(),
        budgets: List<LocalBudget> = emptyList(),
        maxOrder: Int = 4,
    ) = withPlaceholderCategories(categories, expenses, budgets, maxOrder)

    @Test
    fun noOrphans_returnsCategoriesUnchanged() {
        val cats = listOf(backupCategory("gym"))

        val result = run(cats, listOf(backupExpense(categoryId = "gym"), backupExpense("e2")))

        assertEquals(cats, result)
    }

    @Test
    fun orphanExpense_getsArchivedPlaceholder() {
        val result = run(expenses = listOf(backupExpense(categoryId = "gone")))

        val placeholder = result.single()
        assertEquals("gone", placeholder.id)
        assertEquals(PLACEHOLDER_CATEGORY_NAME, placeholder.name)
        assertEquals("Deleted category", placeholder.name)
        assertEquals("label", placeholder.iconKey)
        assertEquals("slate", placeholder.colorKey)
        assertEquals("SPENDING", placeholder.kind)
        assertEquals(5, placeholder.order)
        assertTrue(placeholder.archived)
    }

    @Test
    fun orphanBudget_getsPlaceholder() {
        val result = run(budgets = listOf(LocalBudget("gone", 100)))

        assertEquals(listOf("gone"), result.map { it.id })
    }

    @Test
    fun distinctIds_firstSeenOrder_expensesBeforeBudgets_sequentialOrder() {
        val result =
            run(
                expenses =
                    listOf(
                        backupExpense("e1", categoryId = "b"),
                        backupExpense("e2", categoryId = "a"),
                        backupExpense("e3", categoryId = "b"),
                    ),
                budgets = listOf(LocalBudget("c", 1), LocalBudget("a", 1)),
                maxOrder = 10,
            )

        assertEquals(listOf("b", "a", "c"), result.map { it.id })
        assertEquals(listOf(11, 12, 13), result.map { it.order })
    }

    @Test
    fun builtInsAndExistingCategories_getNoPlaceholder() {
        val result =
            run(
                categories = listOf(backupCategory("gym")),
                expenses = listOf(backupExpense(categoryId = "FOOD"), backupExpense("e2", categoryId = "gym")),
                budgets = listOf(LocalBudget("SHOPPING", 1)),
            )

        assertEquals(listOf("gym"), result.map { it.id })
    }

    @Test
    fun invalidId_getsNoPlaceholder() {
        val result =
            run(
                expenses = listOf(backupExpense(categoryId = "bad id!"), backupExpense("e2", categoryId = "")),
                budgets = listOf(LocalBudget("x".repeat(65), 1)),
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun existingCategoriesKeptFirst() {
        val cats = listOf(backupCategory("gym"))

        val result = run(cats, listOf(backupExpense(categoryId = "gone")))

        assertEquals(listOf("gym", "gone"), result.map { it.id })
    }
}

class BackupPlaceholderNamingTest {
    @Test
    fun namingPlaceholders_renamesOnlyTheAppendedOnes() {
        val stored = listOf(backupCategory("gym"))
        val expenses = listOf(backupExpense(categoryId = "gone"))
        val withPlaceholder = withPlaceholderCategories(stored, expenses, emptyList(), 0)

        val named = withPlaceholder.namingPlaceholders(stored.size, "Categoria apagada")

        assertEquals(listOf(stored.single().name, "Categoria apagada"), named.map { it.name })
        assertEquals(withPlaceholder.map { it.id }, named.map { it.id })
    }

    @Test
    fun namingPlaceholders_withNoPlaceholders_changesNothing() {
        val stored = listOf(backupCategory("gym"))

        assertEquals(stored, stored.namingPlaceholders(stored.size, "X"))
    }
}
