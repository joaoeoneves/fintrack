package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCategoryRepositoryMutationTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalCategoryRepository(store, SequenceIds("c")) }
    private val expenses by lazy { LocalExpenseRepository(store, SequenceIds("e")) }

    private suspend fun catalog() = repo.observeCategories().first()

    private suspend fun add(name: String = "Gym") = repo.addCategory(name, "home", "teal", CategoryKind.SPENDING)

    @Test
    fun setArchived_userCategory_roundTrips() =
        runStoreTest {
            add()
            val id = CategoryId("c1")
            assertTrue(repo.setArchived(id, true).isSuccess)
            assertTrue(catalog().first { it.id == id }.archived)
            assertTrue(repo.setArchived(id, false).isSuccess)
            assertFalse(catalog().first { it.id == id }.archived)
        }

    @Test
    fun setArchived_builtInWithoutOverride_writesFullOverride() =
        runStoreTest {
            assertTrue(repo.setArchived(BuiltInCategories.FOOD.id, true).isSuccess)
            val stored = diskDoc().categories.single()
            assertEquals("FOOD", stored.id)
            assertEquals("restaurant", stored.iconKey)
            assertEquals("violet", stored.colorKey)
            assertEquals("SPENDING", stored.kind)
            assertEquals(BuiltInCategories.FOOD.order, stored.order)
            assertTrue(stored.archived)
            val food = catalog().first { it.id == BuiltInCategories.FOOD.id }
            assertEquals(BuiltInCategories.FOOD.copy(archived = true), food)
        }

    @Test
    fun setArchived_unknownId_failsNothingWritten() =
        runStoreTest {
            assertTrue(repo.setArchived(CategoryId("ghost"), true).exceptionOrNull() is IllegalArgumentException)
            assertFalse(dataFile.exists())
        }

    @Test
    fun setArchived_toSameValue_doesNotBumpRevision() =
        runStoreTest {
            add()
            repo.setArchived(CategoryId("c1"), false)
            assertEquals(1L, store.data.first().revision)
        }

    @Test
    fun reorder_assignsIndexAsOrder() =
        runStoreTest {
            add()
            val ids = listOf(CategoryId("c1"), BuiltInCategories.FOOD.id, BuiltInCategories.TRANSFER.id)
            assertTrue(repo.reorder(ids).isSuccess)
            val byId = catalog().associateBy { it.id }
            ids.forEachIndexed { index, id -> assertEquals(index, byId.getValue(id).order) }
        }

    @Test
    fun reorder_anyUnknownId_failsAndWritesNothing() =
        runStoreTest {
            add()
            val before = dataFile.readText()
            val result = repo.reorder(listOf(CategoryId("c1"), CategoryId("ghost")))
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, dataFile.readText())
            assertEquals(1L, store.data.first().revision)
        }

    @Test
    fun delete_builtIn_fails_customSucceeds() =
        runStoreTest {
            add()
            assertTrue(repo.deleteCategory(BuiltInCategories.FOOD.id).exceptionOrNull() is IllegalArgumentException)
            assertTrue(repo.deleteCategory(CategoryId("c1")).isSuccess)
            assertEquals(BuiltInCategories.all, catalog())
        }

    @Test
    fun delete_missingCustom_succeeds() =
        runStoreTest {
            assertTrue(repo.deleteCategory(CategoryId("nothing")).isSuccess)
        }

    @Test
    fun delete_doesNotTouchReferencingExpenses() =
        runStoreTest {
            add()
            expenses.addExpense(expense("e1", categoryId = "c1"))
            repo.deleteCategory(CategoryId("c1"))
            assertEquals(Category.unknown(CategoryId("c1")), expenses.getExpense("e1").getOrThrow()?.category)
            assertTrue(expenses.hasExpenseWithCategory(CategoryId("c1")).getOrThrow())
        }
}
