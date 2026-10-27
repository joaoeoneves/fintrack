package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCategoryGroupRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val groups by lazy { LocalCategoryGroupRepository(store, SequenceIds("g")) }
    private val categories by lazy { LocalCategoryRepository(store, SequenceIds("c")) }
    private val budgets by lazy { LocalBudgetRepository(store) }

    private suspend fun observed() = groups.observeGroups().first()

    @Test
    fun empty_hasNoGroups() =
        runStoreTest {
            assertEquals(emptyList<CategoryGroup>(), observed())
        }

    @Test
    fun add_appendsWithIncreasingOrder() =
        runStoreTest {
            assertEquals("g1", groups.addGroup("Food").getOrThrow())
            groups.addGroup("Fun")

            assertEquals(listOf(CategoryGroup("g1", "Food", 0), CategoryGroup("g2", "Fun", 1)), observed())
        }

    @Test
    fun add_emptyOrTooLongName_failsAndWritesNothing() =
        runStoreTest {
            assertTrue(groups.addGroup("").exceptionOrNull() is IllegalArgumentException)
            assertTrue(groups.addGroup("x".repeat(MAX_GROUP_NAME_LENGTH + 1)).isFailure)

            assertFalse(dataFile.exists())
        }

    @Test
    fun add_nameAtLimit_isAccepted() =
        runStoreTest {
            assertTrue(groups.addGroup("x".repeat(MAX_GROUP_NAME_LENGTH)).isSuccess)
        }

    @Test
    fun rename_changesNameKeepingOrder() =
        runStoreTest {
            groups.addGroup("Food")

            assertTrue(groups.renameGroup("g1", "Groceries").isSuccess)

            assertEquals(listOf(CategoryGroup("g1", "Groceries", 0)), observed())
        }

    @Test
    fun rename_unknownOrInvalid_fails() =
        runStoreTest {
            groups.addGroup("Food")

            assertTrue(groups.renameGroup("nope", "X").exceptionOrNull() is IllegalArgumentException)
            assertTrue(groups.renameGroup("g1", "").isFailure)
            assertEquals("Food", observed().single().name)
        }

    @Test
    fun reorder_persistsPositions_andObserveSortsByOrder() =
        runStoreTest {
            groups.addGroup("A")
            groups.addGroup("B")
            groups.addGroup("C")

            assertTrue(groups.reorderGroups(listOf("g3", "g1", "g2")).isSuccess)

            assertEquals(listOf("g3", "g1", "g2"), observed().map { it.id })
            assertEquals(listOf(0, 1, 2), observed().map { it.order })
        }

    @Test
    fun reorder_unknownId_failsAndKeepsOrder() =
        runStoreTest {
            groups.addGroup("A")
            groups.addGroup("B")

            assertTrue(groups.reorderGroups(listOf("g2", "zzz")).isFailure)

            assertEquals(listOf("g1", "g2"), observed().map { it.id })
        }

    @Test
    fun delete_removesGroup_andUngroupsItsCategories() =
        runStoreTest {
            val food = groups.addGroup("Food").getOrThrow()
            val other = groups.addGroup("Other").getOrThrow()
            val gym = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING, food).getOrThrow()
            val book = categories.addCategory("Books", "home", "teal", CategoryKind.SPENDING, other).getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)

            assertTrue(groups.deleteGroup(food).isSuccess)

            assertEquals(listOf(other), observed().map { it.id })
            val catalog = categories.observeCategories().first().associateBy { it.id }
            assertNull(catalog.getValue(gym).groupId)
            assertNull(catalog.getValue(BuiltInCategories.FOOD.id).groupId)
            assertEquals(other, catalog.getValue(book).groupId)
        }

    @Test
    fun delete_unknownGroup_isHarmless() =
        runStoreTest {
            groups.addGroup("Food")

            assertTrue(groups.deleteGroup("nope").isSuccess)
            assertEquals(1, observed().size)
        }

    // ---- category side ----

    @Test
    fun setGroup_onUserAndBuiltInCategory_andClear() =
        runStoreTest {
            val food = groups.addGroup("Food").getOrThrow()
            val gym = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING).getOrThrow()

            assertTrue(categories.setGroup(gym, food).isSuccess)
            assertTrue(categories.setGroup(BuiltInCategories.SHOPPING.id, food).isSuccess)
            val catalog = categories.observeCategories().first().associateBy { it.id }
            assertEquals(food, catalog.getValue(gym).groupId)
            assertEquals(food, catalog.getValue(BuiltInCategories.SHOPPING.id).groupId)

            assertTrue(categories.setGroup(gym, null).isSuccess)
            assertNull(
                categories
                    .observeCategories()
                    .first()
                    .single { it.id == gym }
                    .groupId,
            )
        }

    @Test
    fun setGroup_unknownCategory_fails() =
        runStoreTest {
            assertTrue(categories.setGroup(CategoryId("ghost"), "g1").exceptionOrNull() is IllegalArgumentException)
        }

    @Test
    fun setGroup_invalidGroupIdShape_fails() =
        runStoreTest {
            assertTrue(categories.setGroup(BuiltInCategories.FOOD.id, "bad id!").isFailure)
            assertFalse(dataFile.exists())
        }

    @Test
    fun addCategory_withGroup_isStored() =
        runStoreTest {
            val food = groups.addGroup("Food").getOrThrow()

            val id = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING, food).getOrThrow()

            assertEquals(
                food,
                categories
                    .observeCategories()
                    .first()
                    .single { it.id == id }
                    .groupId,
            )
        }

    // ---- budget skip months ----

    @Test
    fun setBudget_storesSkipMonths_andReplacesThemOnNextSet() =
        runStoreTest {
            val food = BuiltInCategories.FOOD.id
            budgets.setBudget(food, 100, setOf(8, 1))
            assertEquals(
                setOf(1, 8),
                budgets
                    .observeBudgets()
                    .first()
                    .single()
                    .skipMonths,
            )
            assertEquals(
                listOf(1, 8),
                store.data
                    .first()
                    .budgets
                    .single()
                    .skipMonths,
            )

            budgets.setBudget(food, 100)
            assertEquals(
                emptySet<Int>(),
                budgets
                    .observeBudgets()
                    .first()
                    .single()
                    .skipMonths,
            )
        }

    @Test
    fun setBudget_invalidSkipMonths_fails() =
        runStoreTest {
            val food = BuiltInCategories.FOOD.id

            assertTrue(budgets.setBudget(food, 100, setOf(0)).isFailure)
            assertTrue(budgets.setBudget(food, 100, setOf(13)).isFailure)
            assertFalse(dataFile.exists())
        }

    @Test
    fun legacyBudgetWithoutSkipMonths_decodesWithEmptySet() =
        runStoreTest {
            dataFile.writeText("""{"schemaVersion":4,"revision":1,"budgets":[{"categoryId":"FOOD","limitCents":5}]}""")

            assertEquals(
                emptySet<Int>(),
                newStore()
                    .data
                    .first()
                    .budgets
                    .single()
                    .skipMonths
                    .toSet(),
            )
        }
}
