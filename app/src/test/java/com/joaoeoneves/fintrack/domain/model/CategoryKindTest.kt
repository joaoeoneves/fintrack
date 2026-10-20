package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryKindTest {
    @Test
    fun everyCategory_mapsToExpectedKind() {
        val expected =
            mapOf(
                ExpenseCategory.TRANSFER to CategoryKind.SPENDING,
                ExpenseCategory.INVESTMENTS to CategoryKind.SAVING,
                ExpenseCategory.SHOPPING to CategoryKind.SPENDING,
                ExpenseCategory.RECURRING to CategoryKind.SPENDING,
            )

        // Exhaustive: a newly added category must be classified here deliberately.
        assertEquals(ExpenseCategory.entries.toSet(), expected.keys)
        for (category in ExpenseCategory.entries) {
            assertEquals(category.name, expected.getValue(category), category.kind)
        }
    }

    @Test
    fun onlyInvestmentsIsSaving() {
        assertEquals(
            listOf(ExpenseCategory.INVESTMENTS),
            ExpenseCategory.entries.filter { it.kind == CategoryKind.SAVING },
        )
    }
}
