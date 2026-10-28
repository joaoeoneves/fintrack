package com.joaoeoneves.fintrack.data.local

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalDataStoreTest : LocalStoreTestBase() {
    private val sample =
        LocalDataDocument(
            expenses =
                listOf(
                    LocalExpense("e1", "Lunch", 1050, "FOOD", "2026-01-31T10:15:30.123456789Z", note = null),
                    LocalExpense("e2", "Shoes", 5000, "SHOPPING", "2026-02-01T00:00:00Z", note = "n", isRefund = true),
                ),
            income = listOf(LocalIncome("i1", "Pay", 9000, "2026-01-01T00:00:00Z")),
            budgets = listOf(LocalBudget("FOOD", 20000)),
            categories = listOf(LocalCategory("c1", "Gym", "home", "teal", "SPENDING", 7)),
        )

    @Test
    fun missingFile_loadsEmptyRevisionZero() =
        runStoreTest {
            val doc = newStore().data.first()
            assertEquals(LocalDataDocument(), doc)
            assertEquals(0L, doc.revision)
            assertFalse(dataFile.exists())
        }

    @Test
    fun write_thenNewInstance_roundTripsIncludingNanosAndNullNotes() =
        runStoreTest {
            assertTrue(newStore().replaceAll(sample).isSuccess)
            val loaded = newStore().data.first()
            assertEquals(sample.copy(revision = 1), loaded)
            assertEquals("2026-01-31T10:15:30.123456789Z", loaded.expenses[0].date)
            assertNull(loaded.expenses[0].note)
        }

    @Test
    fun update_incrementsRevisionOnEachChange() =
        runStoreTest {
            val store = newStore()
            repeat(3) { i ->
                store.update { it.copy(budgets = listOf(LocalBudget("FOOD", 100L + i))) to Unit }
            }
            assertEquals(3L, store.data.first().revision)
            assertEquals(3L, diskDoc().revision)
        }

    @Test
    fun update_unchangedContent_writesNothingAndKeepsRevision() =
        runStoreTest {
            val store = newStore()
            assertTrue(store.update { it to Unit }.isSuccess)
            assertFalse(dataFile.exists())
            store.replaceAll(sample)
            val before = dataFile.readText()
            assertTrue(store.replaceAll(sample).isSuccess)
            assertEquals(before, dataFile.readText())
            assertEquals(1L, store.data.first().revision)
        }

    @Test
    fun update_returnsTransformResult() =
        runStoreTest {
            val result = newStore().update { it.copy(budgets = listOf(LocalBudget("FOOD", 1))) to "ok" }
            assertEquals("ok", result.getOrNull())
        }

    @Test
    fun write_leavesNoTmpFile() =
        runStoreTest {
            newStore().replaceAll(sample)
            assertEquals(listOf("guest-data.json"), filesInDir())
        }

    @Test
    fun staleTmp_isDeletedOnLoad() =
        runStoreTest {
            File(tmp.root, "guest-data.json.tmp").writeText("partial")
            newStore().data.first()
            assertEquals(emptyList<String>(), filesInDir())
        }

    @Test
    fun concurrentUpdates_allPersisted() =
        runStoreTest {
            val store = newStore()
            (1..100)
                .map { i ->
                    async { store.update { doc -> doc.copy(budgets = doc.budgets + LocalBudget("B$i", 1L)) to Unit } }
                }.awaitAll()
                .forEach { assertTrue(it.isSuccess) }
            val reloaded = newStore().data.first()
            assertEquals(100, reloaded.budgets.size)
            assertEquals(100L, reloaded.revision)
            assertEquals((1..100).map { "B$it" }.toSet(), reloaded.budgets.map { it.categoryId }.toSet())
        }

    @Test
    fun unknownJsonKeys_areIgnored() =
        runStoreTest {
            dataFile.writeText(
                """{"schemaVersion":1,"revision":4,"future":{"a":1},""" +
                    """"budgets":[{"categoryId":"FOOD","limitCents":5,"x":true}]}""",
            )
            val doc = newStore().data.first()
            assertEquals(4L, doc.revision)
            assertEquals(listOf(LocalBudget("FOOD", 5)), doc.budgets)
        }

    @Test
    fun encodedFile_omitsNullNotesButWritesDefaults() =
        runStoreTest {
            newStore().replaceAll(sample)
            val text = dataFile.readText()
            assertTrue(text.contains("\"schemaVersion\":12"))
            assertTrue(text.contains("\"isRefund\":false"))
            assertFalse(text.contains("null"))
        }
}
