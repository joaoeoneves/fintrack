package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.MAX_CATEGORY_NAME_LENGTH
import com.joaoeoneves.fintrack.testutil.userCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryNameValidationTest {
    private val gym = userCategory("custom-1", "Gym")
    private val all: List<Category> = BuiltInCategories.all + gym
    private val required = UiText.StringResource(R.string.error_category_name_required)
    private val tooLong = UiText.StringResource(R.string.error_category_name_too_long)
    private val duplicate = UiText.StringResource(R.string.error_category_name_duplicate)

    private fun validateNew(name: String) = validateCategoryName(name, null, all)

    @Test
    fun blank_isRequiredForNewAndUserCategory() {
        assertEquals(required, validateNew(""))
        assertEquals(required, validateCategoryName("", gym, all))
    }

    @Test
    fun whitespaceOnly_countsAsBlank() {
        assertEquals(required, validateNew("   \t "))
        assertNull(validateCategoryName("   ", BuiltInCategories.FOOD, all))
    }

    @Test
    fun blank_isAllowedForBuiltIn() {
        assertNull(validateCategoryName("", BuiltInCategories.SHOPPING, all))
    }

    @Test
    fun length_fiftyCodePointsOk_fiftyOneTooLong() {
        val emoji = "😀" // one code point, two chars
        assertNull(validateNew(emoji.repeat(MAX_CATEGORY_NAME_LENGTH)))
        assertEquals(tooLong, validateNew(emoji.repeat(MAX_CATEGORY_NAME_LENGTH + 1)))
        assertNull(validateNew("a".repeat(MAX_CATEGORY_NAME_LENGTH)))
        assertEquals(tooLong, validateNew("a".repeat(MAX_CATEGORY_NAME_LENGTH + 1)))
    }

    @Test
    fun length_isMeasuredAfterTrimming() {
        assertNull(validateNew("  " + "a".repeat(MAX_CATEGORY_NAME_LENGTH) + "  "))
    }

    @Test
    fun duplicate_ofUserName_ignoresCaseAndPadding() {
        assertEquals(duplicate, validateNew("gym"))
        assertEquals(duplicate, validateNew("  GYM  "))
    }

    @Test
    fun duplicate_ofBuiltInCanonicalName_evenWhenOverridden() {
        val overridden = BuiltInCategories.SHOPPING.copy(customName = "Stuff")
        val catalog = BuiltInCategories.all.map { if (it.id == overridden.id) overridden else it } + gym
        assertEquals(duplicate, validateCategoryName("shopping", null, catalog))
        assertEquals(duplicate, validateCategoryName("Stuff", null, catalog))
    }

    @Test
    fun duplicate_ofBuiltInId() {
        assertEquals(duplicate, validateNew("FOOD"))
        assertEquals(duplicate, validateNew("recurring"))
    }

    @Test
    fun duplicate_ofArchivedName() {
        val catalog = all + userCategory("custom-2", "Old", archived = true)
        assertEquals(duplicate, validateCategoryName("old", null, catalog))
    }

    @Test
    fun ownName_isOkWhileEditing() {
        assertNull(validateCategoryName("Gym", gym, all))
        assertNull(validateCategoryName("gym", gym, all))
    }

    @Test
    fun builtIn_mayTakeOwnCanonicalNameButNotAnothers() {
        assertNull(validateCategoryName("Food", BuiltInCategories.FOOD, all))
        assertNull(validateCategoryName("FOOD", BuiltInCategories.FOOD, all))
        assertEquals(duplicate, validateCategoryName("Shopping", BuiltInCategories.FOOD, all))
        assertEquals(duplicate, validateCategoryName("Gym", BuiltInCategories.FOOD, all))
    }
}
