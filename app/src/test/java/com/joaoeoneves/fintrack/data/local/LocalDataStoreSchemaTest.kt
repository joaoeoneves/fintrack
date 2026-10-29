package com.joaoeoneves.fintrack.data.local

import android.app.Application
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Schema versioning of the guest file: v1 files gain an empty plan on the next write, newer ones are set aside. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalDataStoreSchemaTest : LocalStoreTestBase() {
    private val v1File = """{"schemaVersion":1,"revision":7,"budgets":[{"categoryId":"FOOD","limitCents":5}]}"""

    @Test
    fun currentSchemaVersion_isTwentyTwo() {
        assertEquals(22, LOCAL_SCHEMA_VERSION)
        assertEquals(22, LocalDataDocument().schemaVersion)
    }

    @Test
    fun v1File_loadsWithEmptyPlannedIncome_andKeepsItsData() =
        runStoreTest {
            dataFile.writeText(v1File)

            val doc = newStore().data.first()

            assertEquals(emptyList<LocalPlannedIncome>(), doc.plannedIncome)
            assertEquals(listOf(LocalBudget("FOOD", 5)), doc.budgets.untimedLocal())
            assertEquals(7L, doc.revision)
            assertTrue(filesInDir().none { it.contains("corrupt") })
        }

    @Test
    fun v1File_isNotRewrittenOnRead() =
        runStoreTest {
            dataFile.writeText(v1File)

            newStore().data.first()

            assertEquals(v1File, dataFile.readText())
        }

    @Test
    fun v1File_isRewrittenAsV14_onNextWrite() =
        runStoreTest {
            dataFile.writeText(v1File)
            val store = newStore()
            val line = LocalPlannedIncome("p", "Salary", 100L, 0)

            assertTrue(store.update { it.copy(plannedIncome = listOf(line)) to Unit }.isSuccess)

            val onDisk = diskDoc()
            assertEquals(22, onDisk.schemaVersion)
            assertEquals(8L, onDisk.revision)
            assertEquals(listOf(line), onDisk.plannedIncome)
            assertEquals(listOf(LocalBudget("FOOD", 5)), onDisk.budgets.untimedLocal())
        }

    @Test
    fun v23File_isMovedAside_andStoreStartsEmpty() =
        runStoreTest {
            val content = """{"schemaVersion":23,"revision":5,"plannedIncome":[]}"""
            dataFile.writeText(content)

            assertEquals(LocalDataDocument(), newStore().data.first())

            val aside = filesInDir().filter { it.startsWith("guest-data.corrupt-") }
            assertEquals(1, aside.size)
            assertEquals(content, File(tmp.root, aside.single()).readText())
            assertFalse(dataFile.exists())
        }
}
