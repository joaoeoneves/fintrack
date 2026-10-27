package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryKindTest {
    @Test
    fun everyCategory_mapsToExpectedKind() {
        val expected =
            mapOf(
                BuiltInCategories.TRANSFER to CategoryKind.SPENDING,
                BuiltInCategories.INVESTMENTS to CategoryKind.SAVING,
                BuiltInCategories.SHOPPING to CategoryKind.SPENDING,
                BuiltInCategories.FOOD to CategoryKind.SPENDING,
                BuiltInCategories.RECURRING to CategoryKind.SPENDING,
            )

        // Exhaustive: a newly added category must be classified here deliberately.
        assertEquals(BuiltInCategories.all.toSet(), expected.keys)
        for (category in BuiltInCategories.all) {
            assertEquals(category.id.value, expected.getValue(category), category.kind)
        }
    }

    @Test
    fun onlyInvestmentsIsSaving() {
        assertEquals(
            listOf(BuiltInCategories.INVESTMENTS),
            BuiltInCategories.all.filter { it.kind == CategoryKind.SAVING },
        )
    }
}
