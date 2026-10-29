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

/** Schema v4: `monthSettings` loads from older files, counts for `isEmpty`, and income lines gain defaults. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalDataDocumentMonthSettingsTest : LocalStoreTestBase() {
    private val v3File =
        """{"schemaVersion":3,"revision":9,"plannedIncome":[{"id":"p","label":"Pay","amountCents":5,"order":0}]}"""

    @Test
    fun v3File_loadsWithEmptyMonthSettings_andDefaultedLines() =
        runStoreTest {
            dataFile.writeText(v3File)

            val doc = newStore().data.first()

            assertEquals(emptyList<LocalMonthSettings>(), doc.monthSettings)
            assertEquals("FIXED", doc.plannedIncome.single().basis)
            assertEquals("MONTHLY", doc.plannedIncome.single().frequency)
            assertEquals(9L, doc.revision)
            assertTrue(filesInDir().none { it.contains("corrupt") })
        }

    @Test
    fun v3File_isRewrittenAsV14_onNextWrite_keepingMonthSettings() =
        runStoreTest {
            dataFile.writeText(v3File)

            newStore().update { it.copy(monthSettings = listOf(LocalMonthSettings("2026-10", 15))) to Unit }

            val onDisk = diskDoc()
            assertEquals(19, onDisk.schemaVersion)
            assertEquals(listOf(LocalMonthSettings("2026-10", 15)), onDisk.monthSettings)
            assertEquals(1, onDisk.plannedIncome.size)
        }

    @Test
    fun isEmpty_falseWithOnlyMonthSettings() {
        assertTrue(LocalDataDocument().isEmpty)
        assertFalse(LocalDataDocument(monthSettings = listOf(LocalMonthSettings("2026-10", 15))).isEmpty)
    }

    @Test
    fun v5File_loadsMonthSettingsWithDaysOffZero() =
        runStoreTest {
            dataFile.writeText(
                """{"schemaVersion":5,"revision":2,"monthSettings":[{"month":"2026-10","workingDays":15}]}""",
            )

            val doc = newStore().data.first()

            assertEquals(listOf(LocalMonthSettings("2026-10", 15, 0)), doc.monthSettings)
            assertTrue(filesInDir().none { it.contains("corrupt") })
        }
}
