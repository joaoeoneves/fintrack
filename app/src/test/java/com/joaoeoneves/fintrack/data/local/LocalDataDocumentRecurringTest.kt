package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Schema v3: the `recurring` list loads from older files, counts for `isEmpty`, and survives a round trip. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalDataDocumentRecurringTest : LocalStoreTestBase() {
    private val v2File =
        """{"schemaVersion":2,"revision":4,"plannedIncome":[{"id":"p","label":"Pay","amountCents":5,"order":0}]}"""

    @Test
    fun v2File_loadsWithEmptyRecurring_andKeepsItsData() =
        runStoreTest {
            dataFile.writeText(v2File)

            val doc = newStore().data.first()

            assertEquals(emptyList<LocalRecurringItem>(), doc.recurring)
            assertEquals(1, doc.plannedIncome.size)
            assertEquals(4L, doc.revision)
            assertTrue(filesInDir().none { it.contains("corrupt") })
        }

    @Test
    fun v2File_isRewrittenAsV5_withRecurring_onNextWrite() =
        runStoreTest {
            dataFile.writeText(v2File)
            val item = recurringItem(id = "k").toLocal()

            newStore().update { it.copy(recurring = listOf(item)) to Unit }

            val onDisk = diskDoc()
            assertEquals(5, onDisk.schemaVersion)
            assertEquals(listOf(item), onDisk.recurring)
            assertEquals(1, onDisk.plannedIncome.size)
        }

    @Test
    fun isEmpty_respectsRecurring() {
        assertTrue(LocalDataDocument().isEmpty)
        assertFalse(LocalDataDocument(recurring = listOf(recurringItem().toLocal())).isEmpty)
    }

    @Test
    fun recurringOnly_documentIsNotConsideredEmptyAfterReload() =
        runStoreTest {
            newStore().update { it.copy(recurring = listOf(recurringItem().toLocal())) to Unit }

            assertFalse(newStore().data.first().isEmpty)
        }
}
