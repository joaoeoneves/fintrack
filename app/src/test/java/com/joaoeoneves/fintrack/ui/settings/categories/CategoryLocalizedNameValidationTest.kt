package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeBuiltInCategoryNames
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.testutil.userCategory
import com.joaoeoneves.fintrack.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryLocalizedNameValidationTest {
    private val gym = userCategory("custom-1", "Gym")
    private val all: List<Category> = BuiltInCategories.all + gym
    private val localized = FakeBuiltInCategoryNames().localizedNames()
    private val duplicate = UiText.StringResource(R.string.error_category_name_duplicate)

    private fun validate(
        name: String,
        editing: Category? = null,
        names: Map<CategoryId, Set<String>> = localized,
    ) = validateCategoryName(name, editing, all, names)

    @Test
    fun newCategory_namedAfterLocalizedBuiltIn_isDuplicate() {
        assertEquals(duplicate, validate("Compras"))
        assertEquals(duplicate, validate(" compras "))
        assertEquals(duplicate, validate("Alimentação"))
        assertEquals(duplicate, validate("ALIMENTAÇÃO"))
    }

    @Test
    fun editingBuiltIn_toItsOwnLocalizedName_isAllowed() {
        assertNull(validate("Compras", BuiltInCategories.SHOPPING))
        assertNull(resolveCustomName("Compras", BuiltInCategories.SHOPPING, localized))
        assertNull(resolveCustomName(" compras ", BuiltInCategories.SHOPPING, localized))
    }

    @Test
    fun editingBuiltIn_toOtherBuiltInsLocalizedName_isDuplicate() {
        assertEquals(duplicate, validate("Alimentação", BuiltInCategories.SHOPPING))
    }

    @Test
    fun resolveCustomName_builtInOtherName_isKept() {
        assertEquals("Mercado", resolveCustomName(" Mercado ", BuiltInCategories.SHOPPING, localized))
        assertEquals("Alimentação", resolveCustomName("Alimentação", BuiltInCategories.SHOPPING, localized))
    }

    @Test
    fun resolveCustomName_userCategory_neverTreatedAsDefault() {
        assertEquals("Compras", resolveCustomName("Compras", gym, localized))
    }

    @Test
    fun editingUserCategory_ownNameAndUnrelatedNameAreOk() {
        assertNull(validate("Gym", gym))
        assertNull(validate("gym", gym))
        assertNull(validate("Ginásio", gym))
        assertEquals(duplicate, validate("Compras", gym))
    }

    @Test
    fun emptyMap_keepsOnlyEnglishNamesAndIdsReserved() {
        assertNull(validate("Compras", names = emptyMap()))
        assertNull(validate("Alimentação", names = emptyMap()))
        assertEquals(duplicate, validate("Shopping", names = emptyMap()))
        assertEquals(duplicate, validate(BuiltInCategories.FOOD.id.value, names = emptyMap()))
    }

    @Test
    fun defaultParameter_behavesLikeEmptyMap() {
        assertNull(validateCategoryName("Compras", null, all))
        assertEquals(duplicate, validateCategoryName("food", null, all))
        assertEquals("Compras", resolveCustomName("Compras", BuiltInCategories.SHOPPING))
        assertNull(resolveCustomName("Shopping", BuiltInCategories.SHOPPING))
    }
}
