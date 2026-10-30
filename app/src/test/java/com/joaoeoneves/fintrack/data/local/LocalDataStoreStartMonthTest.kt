package com.joaoeoneves.fintrack.data.local

import android.app.Application
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A v18 guest file gains start months on read; income lines keep it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalDataStoreStartMonthTest : LocalStoreTestBase() {
    private val v18File =
        """{"schemaVersion":18,"revision":4,
        |"expenses":[{"id":"e1","name":"x","amountCents":100,"categoryId":"FOOD","date":"2026-04-10T12:00:00Z"}],
        |"plannedIncome":[{"id":"p","label":"Pay","amountCents":1000,"order":0}]}
        """.trimMargin()

    @Test
    fun v18File_getsStartMonthsOnRead_withoutLosingData() =
        runStoreTest {
            dataFile.writeText(v18File)

            val doc = newStore().data.first()

            assertEquals("2026-04", doc.plannedIncome.single().startMonth)
            // Only upgraded in memory to the start-month schema; the next write stamps the current version.
            assertEquals(LOCAL_SCHEMA_VERSION, doc.schemaVersion)
            assertEquals(4L, doc.revision)
            assertEquals(1, doc.expenses.size)
            assertTrue(filesInDir().none { it.contains("corrupt") })
        }

    @Test
    fun v18File_isNotRewrittenOnRead() =
        runStoreTest {
            dataFile.writeText(v18File)

            newStore().data.first()

            assertEquals(v18File, dataFile.readText())
        }

    @Test
    fun v18File_nextWritePersistsCurrentSchema() =
        runStoreTest {
            dataFile.writeText(v18File)
            val store = newStore()

            assertTrue(store.update { it.copy(dismissedRecurringSuggestions = listOf("x")) to Unit }.isSuccess)

            val onDisk = diskDoc()
            assertEquals(LOCAL_SCHEMA_VERSION, onDisk.schemaVersion)
            assertEquals("2026-04", onDisk.plannedIncome.single().startMonth)
        }
}
