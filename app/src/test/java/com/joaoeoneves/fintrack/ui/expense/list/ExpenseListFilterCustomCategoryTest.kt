package com.joaoeoneves.fintrack.ui.expense.list

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [matchesQuery] against user-defined categories. */
class ExpenseListFilterCustomCategoryTest {
    private val custom = userCategory(id = "k9Zx2Qa7", name = "Gym")
    private val expense =
        Expense(
            id = "e",
            name = "Monthly fee",
            amountCents = 2_500L,
            category = custom,
            date = Instant.parse("2026-10-20T12:00:00Z"),
            note = null,
        )

    @Test
    fun customCategoryName_matches_caseInsensitive() {
        assertTrue(expense.matchesQuery("gym", emptyMap()))
        assertTrue(expense.matchesQuery("GY", emptyMap()))
    }

    @Test
    fun idKeyedLabel_matches() {
        assertTrue(expense.matchesQuery("fitness", mapOf(custom.id to "Fitness club")))
        assertFalse(expense.matchesQuery("fitness", mapOf(CategoryId("other") to "Fitness club")))
    }

    @Test
    fun builtInId_matches_butGeneratedCustomId_doesNot() {
        val food = expense.copy(category = BuiltInCategories.FOOD)

        assertTrue(food.matchesQuery("FOOD", emptyMap()))
        assertFalse(expense.matchesQuery("k9zx", emptyMap()))
        assertFalse(expense.matchesQuery("k9Zx2Qa7", emptyMap()))
    }
}
