package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryEditingTest {
    private fun ids(vararg raw: String) = raw.map { CategoryId(it) }

    private val abc: List<Category> =
        listOf(
            userCategory("a", order = 0),
            userCategory("b", order = 1),
            userCategory("c", order = 2),
        )

    // ---- resolveCustomName ----

    @Test
    fun resolveCustomName_trimsAndKeeps() {
        assertEquals("Gym", resolveCustomName("  Gym ", null))
        assertEquals("Gym", resolveCustomName("Gym", userCategory("x")))
    }

    @Test
    fun resolveCustomName_blankIsNull() {
        assertNull(resolveCustomName("   ", null))
        assertNull(resolveCustomName("", BuiltInCategories.FOOD))
    }

    @Test
    fun resolveCustomName_builtInOwnCanonicalNameIsNull() {
        assertNull(resolveCustomName("food", BuiltInCategories.FOOD))
        assertNull(resolveCustomName(" Food ", BuiltInCategories.FOOD))
    }

    @Test
    fun resolveCustomName_builtInOtherNameIsKept() {
        assertEquals("Meals", resolveCustomName("Meals", BuiltInCategories.FOOD))
    }

    // ---- moved ----

    @Test
    fun moved_upAndDownSwapWithNeighbour() {
        assertEquals(ids("b", "a", "c"), abc.moved(CategoryId("b"), MoveDirection.UP))
        assertEquals(ids("a", "c", "b"), abc.moved(CategoryId("b"), MoveDirection.DOWN))
    }

    @Test
    fun moved_atEndsIsNoOp() {
        assertEquals(ids("a", "b", "c"), abc.moved(CategoryId("a"), MoveDirection.UP))
        assertEquals(ids("a", "b", "c"), abc.moved(CategoryId("c"), MoveDirection.DOWN))
    }

    @Test
    fun moved_excludesArchived() {
        val list = abc + userCategory("z", order = 3, archived = true)
        assertEquals(ids("a", "b", "c"), list.moved(CategoryId("c"), MoveDirection.DOWN))
        assertEquals(ids("a", "b", "c"), list.moved(CategoryId("z"), MoveDirection.UP))
        val archivedMiddle = listOf(abc[0], userCategory("m", order = 1, archived = true), abc[2])
        assertEquals(ids("c", "a"), archivedMiddle.moved(CategoryId("a"), MoveDirection.DOWN))
    }

    @Test
    fun moved_unknownIdIsNoOp() {
        assertEquals(ids("a", "b", "c"), abc.moved(CategoryId("nope"), MoveDirection.UP))
        assertEquals(ids("a", "b", "c"), abc.moved(CategoryId("nope"), MoveDirection.DOWN))
    }

    // ---- newCategoryDefaults ----

    @Test
    fun newCategoryDefaults_labelSpendingEmptyName() {
        val draft = newCategoryDefaults(emptyList())
        assertEquals("", draft.name)
        assertEquals("label", draft.iconKey)
        assertEquals(CategoryKind.SPENDING, draft.kind)
    }

    @Test
    fun newCategoryDefaults_firstUnusedColor() {
        val keys = listOf("red", "green", "blue")
        val used = listOf(userCategory("a").copy(colorKey = "red"))
        assertEquals("green", newCategoryDefaults(used, keys).colorKey)
        assertEquals("red", newCategoryDefaults(emptyList(), keys).colorKey)
    }

    @Test
    fun newCategoryDefaults_archivedColorsCountAsUsed() {
        val keys = listOf("red", "green")
        val used = listOf(userCategory("a", archived = true).copy(colorKey = "red"))
        assertEquals("green", newCategoryDefaults(used, keys).colorKey)
    }

    @Test
    fun newCategoryDefaults_allUsedFallsBackToFirst() {
        val keys = listOf("red", "green")
        val used = keys.mapIndexed { i, k -> userCategory("c$i").copy(colorKey = k) }
        assertEquals("red", newCategoryDefaults(used, keys).colorKey)
    }
}
