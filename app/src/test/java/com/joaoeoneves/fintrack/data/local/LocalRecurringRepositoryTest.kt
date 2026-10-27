package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [LocalRecurringRepository] CRUD, ordering, cursor updates and persistence. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalRecurringRepositoryTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalRecurringRepository(store, SequenceIds("r")) }

    private suspend fun items() = repo.observeRecurring().first()

    @Test
    fun observe_empty() =
        runStoreTest {
            assertEquals(emptyList<RecurringItem>(), items())
        }

    @Test
    fun save_blankId_generatesOne_andReturnsIt() =
        runStoreTest {
            val id = repo.saveRecurring(recurringItem(id = "")).getOrThrow()

            assertEquals("r1", id)
            assertEquals(listOf(recurringItem(id = "r1")), items())
        }

    @Test
    fun save_existingId_replacesInPlace() =
        runStoreTest {
            repo.saveRecurring(recurringItem(id = "k"))

            repo.saveRecurring(recurringItem(id = "k", name = "Flat", amountCents = 7))

            assertEquals(listOf(recurringItem(id = "k", name = "Flat", amountCents = 7)), items())
        }

    @Test
    fun observe_sortsByStartDateThenId() =
        runStoreTest {
            repo.saveRecurring(recurringItem(id = "b", startDate = day(2026, 2, 1)))
            repo.saveRecurring(recurringItem(id = "z", startDate = day(2026, 1, 1)))
            repo.saveRecurring(recurringItem(id = "a", startDate = day(2026, 2, 1)))

            assertEquals(listOf("z", "a", "b"), items().map { it.id })
        }

    @Test
    fun delete_removesItem_missingIsNoOpSuccessWithoutRevisionBump() =
        runStoreTest {
            repo.saveRecurring(recurringItem(id = "k"))

            assertTrue(repo.deleteRecurring("k").isSuccess)
            assertTrue(items().isEmpty())
            val revision = store.data.first().revision
            assertTrue(repo.deleteRecurring("k").isSuccess)
            assertEquals(revision, store.data.first().revision)
        }

    @Test
    fun setHandledThrough_setsAndClearsTheCursor() =
        runStoreTest {
            repo.saveRecurring(recurringItem(id = "k"))

            assertTrue(repo.setHandledThrough("k", day(2026, 3, 15)).isSuccess)
            assertEquals(day(2026, 3, 15), items().single().handledThrough)
            assertTrue(repo.setHandledThrough("k", null).isSuccess)
            assertNull(items().single().handledThrough)
        }

    @Test
    fun setHandledThrough_missingItem_failsAndWritesNothing() =
        runStoreTest {
            assertTrue(repo.setHandledThrough("nope", day(2026, 3, 15)).isFailure)

            assertFalse(dataFile.exists())
        }

    @Test
    fun observe_dropsMalformedRecords_keepsTheRest() =
        runStoreTest {
            val bad = recurringItem(id = "bad").toLocal().copy(frequency = "WEEKLY")
            val good = recurringItem(id = "good").toLocal()
            store.update { it.copy(recurring = listOf(bad, good)) to Unit }

            assertEquals(listOf("good"), items().map { it.id })
        }

    @Test
    fun items_persistAcrossInstances() =
        runStoreTest {
            val item = recurringItem(id = "k", endDate = day(2027, 1, 1), handledThrough = day(2026, 5, 15))
            repo.saveRecurring(item)

            val reloaded = LocalRecurringRepository(newStore(), SequenceIds()).observeRecurring().first()

            assertEquals(listOf(item), reloaded)
        }
}
