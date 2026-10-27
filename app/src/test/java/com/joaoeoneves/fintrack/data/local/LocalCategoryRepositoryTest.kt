package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCategoryRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalCategoryRepository(store, SequenceIds("c")) }

    private suspend fun catalog() = repo.observeCategories().first()

    private suspend fun add(name: String = "Gym") = repo.addCategory(name, "home", "teal", CategoryKind.SPENDING)

    @Test
    fun empty_isBuiltInCatalog() =
        runStoreTest {
            assertEquals(BuiltInCategories.all, catalog())
        }

    @Test
    fun add_firstUserCategoryGetsMaxBuiltInOrderPlusOne_thenIncrements() =
        runStoreTest {
            val maxBuiltIn = BuiltInCategories.all.maxOf { it.order }
            assertEquals(CategoryId("c1"), add().getOrThrow())
            add("Two")
            val byId = catalog().associateBy { it.id.value }
            assertEquals(maxBuiltIn + 1, byId.getValue("c1").order)
            assertEquals(maxBuiltIn + 2, byId.getValue("c2").order)
            assertFalse(byId.getValue("c1").builtIn)
            assertEquals("Gym", byId.getValue("c1").customName)
        }

    @Test
    fun add_ignoresUnknownPlaceholderOrdersWhenComputingNext() =
        runStoreTest {
            // An override with Int.MAX_VALUE order must not push the next order to overflow.
            store.seedCategory("FOOD", name = null, order = Int.MAX_VALUE)
            val maxOther = catalog().map { it.order }.filter { it != Int.MAX_VALUE }.max()
            add()
            assertEquals(maxOther + 1, catalog().first { it.id.value == "c1" }.order)
        }

    @Test
    fun add_nameBoundary_andBadIconKey() =
        runStoreTest {
            assertTrue(add("n".repeat(50)).isSuccess)
            assertTrue(add("n".repeat(51)).exceptionOrNull() is IllegalArgumentException)
            assertTrue(add("").isFailure)
            assertTrue(repo.addCategory("X", "Bad Icon", "teal", CategoryKind.SPENDING).isFailure)
            assertTrue(repo.addCategory("X", "home", "RED", CategoryKind.SPENDING).isFailure)
            assertEquals(1, diskDoc().categories.size)
        }

    @Test
    fun add_kindIsStored() =
        runStoreTest {
            repo.addCategory("Save", "home", "teal", CategoryKind.SAVING)
            assertEquals(CategoryKind.SAVING, catalog().first { it.id.value == "c1" }.kind)
        }

    @Test
    fun builtInOverride_roundTripsWithNullName() =
        runStoreTest {
            val food = BuiltInCategories.FOOD
            assertTrue(repo.updateCategory(food.copy(iconKey = "home", colorKey = "teal", order = 9)).isSuccess)
            assertNull(diskDoc().categories.single().name)
            val reloaded = LocalCategoryRepository(newStore(), SequenceIds()).observeCategories().first()
            val loaded = reloaded.first { it.id == food.id }
            assertEquals(food.copy(iconKey = "home", colorKey = "teal", order = 9), loaded)
            assertTrue(loaded.builtIn)
            assertNull(loaded.customName)
        }

    @Test
    fun updateCategory_userCategoryWithoutName_fails() =
        runStoreTest {
            val bad = Category(CategoryId("x1"), null, "home", "teal", CategoryKind.SPENDING, 1, false, false)
            assertTrue(repo.updateCategory(bad).isFailure)
            assertFalse(dataFile.exists())
        }

    @Test
    fun updateCategory_renamesUserCategory() =
        runStoreTest {
            add()
            repo.updateCategory(catalog().first { it.id.value == "c1" }.copy(customName = "Renamed"))
            assertEquals("Renamed", catalog().first { it.id.value == "c1" }.customName)
            assertEquals(1, diskDoc().categories.size)
        }
}
