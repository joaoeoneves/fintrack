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

/** Corrupt / newer-schema handling; these paths call android.util.Log, hence Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalDataStoreRecoveryTest : LocalStoreTestBase() {
    private fun asideFiles() =
        tmp.root
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith("guest-data.corrupt-") }

    private suspend fun assertMovedAsideAndEmpty(content: String) {
        dataFile.writeText(content)
        val store = newStore()
        assertEquals(LocalDataDocument(), store.data.first())
        val aside = asideFiles()
        assertEquals(1, aside.size)
        assertTrue(aside[0].name.matches(Regex("guest-data\\.corrupt-\\d+\\.json")))
        assertEquals(content, aside[0].readText())
        assertFalse(dataFile.exists())
        // Next write succeeds and persists.
        assertTrue(store.update { it.copy(budgets = listOf(LocalBudget("FOOD", 1))) to Unit }.isSuccess)
        assertEquals(1L, newStore().data.first().revision)
    }

    @Test
    fun garbage_isMovedAside() = runStoreTest { assertMovedAsideAndEmpty("not json {{{") }

    @Test
    fun zeroByteFile_isCorrupt() = runStoreTest { assertMovedAsideAndEmpty("") }

    @Test
    fun wrongShape_isCorrupt() = runStoreTest { assertMovedAsideAndEmpty("""{"expenses":[{"id":1}]}""") }

    @Test
    fun newerSchema_isMovedAside() = runStoreTest { assertMovedAsideAndEmpty("""{"schemaVersion":99,"revision":5}""") }

    @Test
    fun schemaVersionOne_isLoadedNotMoved() =
        runStoreTest {
            dataFile.writeText("""{"schemaVersion":1,"revision":3}""")
            assertEquals(3L, newStore().data.first().revision)
            assertTrue(asideFiles().isEmpty())
        }

    @Test
    fun staleTmpAndCorruptFile_bothHandled() =
        runStoreTest {
            File(tmp.root, "guest-data.json.tmp").writeText("x")
            dataFile.writeText("garbage")
            newStore().data.first()
            assertFalse(File(tmp.root, "guest-data.json.tmp").exists())
            assertEquals(1, asideFiles().size)
        }
}
