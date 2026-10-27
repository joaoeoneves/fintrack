package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.data.csv.canonicalName
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryNamesTest {
    @Test
    fun effectiveName_customNameWins() {
        assertEquals("Mine", BuiltInCategories.FOOD.copy(customName = "Mine").effectiveName())
        assertEquals("Gym", userCategory("custom-1", "Gym").effectiveName())
    }

    @Test
    fun effectiveName_builtInsFallBackToEnglishName() {
        val expected =
            mapOf(
                BuiltInCategories.TRANSFER to "Transfer",
                BuiltInCategories.INVESTMENTS to "Investments",
                BuiltInCategories.SHOPPING to "Shopping",
                BuiltInCategories.FOOD to "Food",
                BuiltInCategories.RECURRING to "Recurring",
            )
        expected.forEach { (category, name) -> assertEquals(name, category.effectiveName()) }
    }

    @Test
    fun effectiveName_unknownWithoutCustomName_isRawId() {
        assertEquals("ghost", Category.unknown(CategoryId("ghost")).effectiveName())
    }

    @Test
    fun canonicalNameOf_knownAndUnknown() {
        assertEquals("Food", canonicalNameOf(BuiltInCategories.FOOD.id))
        assertNull(canonicalNameOf(CategoryId("custom-1")))
    }

    @Test
    fun builtInEnglishNames_coversEveryBuiltIn() {
        assertEquals(BuiltInCategories.all.map { it.id }.toSet(), builtInEnglishNames.keys)
    }

    @Test
    fun csvCanonicalName_matchesEffectiveName() {
        val categories =
            BuiltInCategories.all +
                BuiltInCategories.FOOD.copy(customName = "Meals") +
                userCategory("custom-1", "Gym") +
                Category.unknown(CategoryId("ghost"))
        categories.forEach { assertEquals(it.effectiveName(), it.canonicalName()) }
    }
}
