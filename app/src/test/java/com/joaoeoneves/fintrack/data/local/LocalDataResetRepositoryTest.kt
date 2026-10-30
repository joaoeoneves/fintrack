package com.joaoeoneves.fintrack.data.local

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalDataResetRepositoryTest : LocalStoreTestBase() {
    private suspend fun LocalDataStore.seed() =
        update {
            it.copy(
                expenses = listOf(LocalExpense("e1", "Lunch", 1050, "FOOD", "2026-01-31T10:15:30Z", note = null)),
                income = listOf(LocalIncome("i1", "Pay", 9000, "2026-01-01T00:00:00Z")),
                monthSettings = listOf(LocalMonthSettings("2026-10", 2)),
                categories = listOf(LocalCategory("c1", "Gym", "home", "teal", "SPENDING", 0)),
            ) to Unit
        }

    @Test
    fun withData_endsEmptyAtCurrentSchema_andBumpsRevision() =
        runStoreTest {
            val store = newStore()
            store.seed()
            val before = store.data.first().revision

            assertTrue(LocalDataResetRepository(store).deleteAllData().isSuccess)

            val doc = store.data.first()
            assertEquals(LocalDataDocument(revision = before + 1), doc)
            assertEquals(LOCAL_SCHEMA_VERSION, doc.schemaVersion)
            assertEquals(doc, diskDoc())
        }

    @Test
    fun emptyStore_succeedsAndStaysEmpty() =
        runStoreTest {
            val store = newStore()

            assertTrue(LocalDataResetRepository(store).deleteAllData().isSuccess)

            val doc = store.data.first()
            assertTrue(doc.expenses.isEmpty() && doc.income.isEmpty() && doc.categories.isEmpty())
            assertEquals(LOCAL_SCHEMA_VERSION, doc.schemaVersion)
        }

    @Test
    fun survivesNewInstance_afterWipe() =
        runStoreTest {
            val store = newStore()
            store.seed()
            LocalDataResetRepository(store).deleteAllData()

            val reloaded = newStore().data.first()
            assertTrue(reloaded.expenses.isEmpty() && reloaded.monthSettings.isEmpty())
        }

    @Test
    fun writeFailure_isReturnedAndDataKept() =
        runStoreTest {
            val dir = File(tmp.root, "sub").apply { mkdirs() }
            val store = newStore(File(dir, "guest-data.json"))
            store.seed()
            dir.deleteRecursively()
            dir.writeText("not a directory")

            val result = LocalDataResetRepository(store).deleteAllData()

            assertTrue(result.isFailure)
            assertEquals(
                1,
                store.data
                    .first()
                    .expenses.size,
            )
        }
}
