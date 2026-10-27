package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [BuiltInCategories], [Category.unknown] and the pure catalog helpers in CategoryCatalog.kt. */
class CategoryCatalogTest {
    private val gym = userCategory("gym", "Gym", order = 5)
    private val pets = userCategory("pets", "Pets", order = 5)
    private val date = Instant.parse("2026-10-10T12:00:00Z")

    // ---- BuiltInCategories ----

    @Test
    fun builtIns_idsOrderAndKinds() {
        assertEquals(
            listOf("TRANSFER", "INVESTMENTS", "SHOPPING", "FOOD", "RECURRING"),
            BuiltInCategories.all.map { it.id.value },
        )
        assertEquals(listOf(0, 1, 2, 3, 4), BuiltInCategories.all.map { it.order })
        assertEquals(
            listOf(BuiltInCategories.INVESTMENTS),
            BuiltInCategories.all.filter { it.kind == CategoryKind.SAVING },
        )
        BuiltInCategories.all.forEach {
            assertTrue(it.builtIn)
            assertFalse(it.archived)
            assertNull(it.customName)
            assertFalse(it.isUnknown)
        }
    }

    @Test
    fun builtIns_byIdAndIsBuiltIn() {
        BuiltInCategories.all.forEach { assertSame(it, BuiltInCategories.byId(it.id)) }
        assertNull(BuiltInCategories.byId(CategoryId("shopping")))
        assertNull(BuiltInCategories.byId(CategoryId("")))
        assertTrue(BuiltInCategories.isBuiltIn(CategoryId("FOOD")))
        assertFalse(BuiltInCategories.isBuiltIn(CategoryId("gym")))
    }

    @Test
    fun unknownPlaceholder_isArchivedSpendingSortedLast() {
        val unknown = Category.unknown(CategoryId("gone"))

        assertTrue(unknown.isUnknown)
        assertTrue(unknown.archived)
        assertFalse(unknown.builtIn)
        assertEquals(CategoryKind.SPENDING, unknown.kind)
        assertEquals(Int.MAX_VALUE, unknown.order)
    }

    // ---- mergeCategories ----

    @Test
    fun merge_noDocs_isBuiltIns() {
        assertEquals(BuiltInCategories.all, mergeCategories(emptyList()))
    }

    @Test
    fun merge_overrideDoc_replacesBuiltInFields_andForcesBuiltIn() {
        val override =
            BuiltInCategories.FOOD.copy(
                customName = "Groceries",
                kind = CategoryKind.SAVING,
                order = 9,
                builtIn = false,
            )

        val merged = mergeCategories(listOf(override))

        assertEquals(5, merged.size)
        val food = merged.single { it.id == BuiltInCategories.FOOD.id }
        assertEquals("Groceries", food.customName)
        assertEquals(CategoryKind.SAVING, food.kind)
        assertTrue(food.builtIn)
        assertEquals(BuiltInCategories.FOOD.id, merged.last().id)
    }

    @Test
    fun merge_userDocs_appended_sortedByOrderThenId_archivedIncluded_deduplicated() {
        val archived = userCategory("old", "Old", order = 1, archived = true)

        val merged = mergeCategories(listOf(pets, gym, archived, gym))

        assertEquals(
            listOf("TRANSFER", "INVESTMENTS", "old", "SHOPPING", "FOOD", "RECURRING", "gym", "pets"),
            merged.map { it.id.value },
        )
    }

    // ---- resolve / active / optionsIncluding / defaultCategory ----

    @Test
    fun resolve_catalogEntry_builtInFallback_unknownPlaceholder() {
        val renamedShopping = BuiltInCategories.SHOPPING.copy(customName = "Stuff")
        val catalog = listOf(renamedShopping, gym)

        assertEquals(renamedShopping, catalog.resolve(BuiltInCategories.SHOPPING.id))
        assertEquals(gym, catalog.resolve(gym.id))
        assertEquals(BuiltInCategories.FOOD, catalog.resolve(BuiltInCategories.FOOD.id))
        assertEquals(Category.unknown(CategoryId("x")), catalog.resolve(CategoryId("x")))
    }

    @Test
    fun optionsIncluding_appendsArchivedOrUnknownCurrent_once() {
        val archived = userCategory("old", archived = true)
        val catalog = mergeCategories(listOf(gym, archived))

        assertFalse(catalog.active().contains(archived))
        assertEquals(catalog.active(), catalog.optionsIncluding(null))
        assertEquals(catalog.active(), catalog.optionsIncluding(gym))
        assertEquals(catalog.active() + archived, catalog.optionsIncluding(archived))
        val unknown = Category.unknown(CategoryId("gone"))
        assertEquals(unknown, catalog.optionsIncluding(unknown).last())
    }

    @Test
    fun defaultCategory_shoppingWhenActive_elseFirstActive_elseShopping() {
        assertEquals(BuiltInCategories.SHOPPING, defaultCategory(BuiltInCategories.all))

        val shoppingArchived = mergeCategories(listOf(BuiltInCategories.SHOPPING.copy(archived = true)))
        assertEquals(BuiltInCategories.TRANSFER, defaultCategory(shoppingArchived))

        val allArchived = BuiltInCategories.all.map { it.copy(archived = true) }
        assertEquals(BuiltInCategories.SHOPPING, defaultCategory(allArchived))
        assertEquals(BuiltInCategories.SHOPPING, defaultCategory(emptyList()))
    }

    // ---- resolvedWith ----

    @Test
    fun resolvedWith_replacesStaleCategoryCopies_andMarksMissingAsUnknown() {
        val renamedGym = gym.copy(customName = "Fitness")
        val expenses =
            listOf(
                testExpense("a", 1L, date, gym),
                testExpense("b", 1L, date, BuiltInCategories.FOOD),
                testExpense("c", 1L, date, userCategory("deleted")),
            )

        val resolved = expenses.resolvedWith(mergeCategories(listOf(renamedGym)))

        assertEquals(listOf(renamedGym, BuiltInCategories.FOOD), resolved.take(2).map { it.category })
        assertTrue(resolved[2].category.isUnknown)
        assertSame(expenses[1], resolved[1])
    }

    // ---- rowsFor ----

    @Test
    fun rowsFor_activePlusPresentArchived_unknownLast() {
        val archivedWithData = userCategory("old", order = 0, archived = true)
        val archivedNoData = userCategory("older", order = 0, archived = true)
        val catalog = mergeCategories(listOf(gym, archivedWithData, archivedNoData))

        val rows = catalog.rowsFor(setOf(archivedWithData.id, CategoryId("zzz"), CategoryId("aaa")))

        assertEquals(
            listOf("TRANSFER", "old", "INVESTMENTS", "SHOPPING", "FOOD", "RECURRING", "gym", "aaa", "zzz"),
            rows.map { it.id.value },
        )
        assertTrue(rows.takeLast(2).all { it.isUnknown })
    }

    @Test
    fun rowsFor_emptyPresent_isActiveOnly() {
        val catalog = mergeCategories(listOf(userCategory("old", archived = true)))

        assertEquals(BuiltInCategories.all, catalog.rowsFor(emptySet()))
    }
}
