package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.data.session.EnglishStarterNames
import com.joaoeoneves.fintrack.domain.model.StarterCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestStarterSeederTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val seeder by lazy { GuestStarterSeeder(store, EnglishStarterNames) }

    private suspend fun LocalDataStore.categoryList() = update { it to it.categories }.getOrThrow()

    private suspend fun LocalDataStore.addExpense() =
        update {
            it.copy(expenses = listOf(LocalExpense("e1", "Lunch", 1000, "FOOD", "2026-03-10T12:00:00Z"))) to Unit
        }

    private suspend fun LocalDataStore.rename(
        id: String,
        name: String,
    ) = update { doc ->
        doc.copy(categories = doc.categories.map { if (it.id == id) it.copy(name = name) else it }) to Unit
    }

    @Test
    fun untouchedStore_getsTheFourStarters_withSpecAttributes() =
        runStoreTest {
            assertTrue(seeder.seedIfUntouched().isSuccess)

            val seeded = store.categoryList()
            assertEquals(StarterCategories.ids, seeded.map { it.id }.toSet())
            assertEquals(listOf("Home", "Transport", "Health", "Leisure"), seeded.sortedBy { it.order }.map { it.name })
            seeded.forEach {
                val spec = StarterCategories.specOf(it.id)!!
                assertEquals(spec.iconKey, it.iconKey)
                assertEquals(spec.colorKey, it.colorKey)
                assertEquals(spec.order, it.order)
                assertEquals("SPENDING", it.kind)
            }
        }

    @Test
    fun seedingTwice_doesNotDuplicate() =
        runStoreTest {
            seeder.seedIfUntouched()
            seeder.seedIfUntouched()

            assertEquals(4, store.categoryList().size)
        }

    @Test
    fun storeWithAnExpense_isNotSeeded() =
        runStoreTest {
            store.addExpense()

            seeder.seedIfUntouched()

            assertEquals(emptyList<LocalCategory>(), store.categoryList())
        }

    @Test
    fun storeWithACustomCategory_isNotSeeded() =
        runStoreTest {
            store.seedCategory("c1")

            seeder.seedIfUntouched()

            assertEquals(listOf("c1"), store.categoryList().map { it.id })
        }

    @Test
    fun storeWithOnlyASalaryProfileOrMonthSettings_isNotSeeded() =
        runStoreTest {
            store.update { it.copy(monthSettings = listOf(LocalMonthSettings("2026-10", sickDays = 1))) to Unit }

            seeder.seedIfUntouched()

            assertTrue(store.categoryList().isEmpty())
        }

    @Test
    fun seededStore_stillCountsAsEmpty() =
        runStoreTest {
            seeder.seedIfUntouched()

            assertTrue(store.update { it to it.isEmpty }.getOrThrow())
        }

    @Test
    fun renamedStarter_makesTheStoreNonEmpty() =
        runStoreTest {
            seeder.seedIfUntouched()
            store.rename(StarterCategories.HOME_ID, "Rent")

            assertFalse(store.update { it to it.isEmpty }.getOrThrow())
        }

    @Test
    fun starterWithAnotherLanguagesName_isStillUntouched() =
        runStoreTest {
            seeder.seedIfUntouched()
            store.rename(StarterCategories.HOME_ID, "Casa")

            assertTrue(store.update { it to it.isEmpty }.getOrThrow())
        }

    @Test
    fun expenseOnTopOfStarters_makesTheStoreNonEmpty() =
        runStoreTest {
            seeder.seedIfUntouched()
            store.addExpense()

            assertFalse(store.update { it to it.isEmpty }.getOrThrow())
        }
}
