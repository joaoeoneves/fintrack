package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [LocalRecurringRepository.skipOccurrences] / `unskipOccurrences`. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalRecurringRepositorySkipTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalRecurringRepository(store, SequenceIds("r")) }
    private val feb15 = day(2026, 2, 15)
    private val mar15 = day(2026, 3, 15)

    private suspend fun item() = repo.observeRecurring().first().single()

    @Test
    fun skip_unionsTheDates_andKeepsTheCursorWhenNotAdvancing() =
        runStoreTest {
            repo.saveRecurring(recurringItem(id = "k", handledThrough = day(2026, 1, 15), skipped = setOf(feb15)))

            assertTrue(repo.skipOccurrences("k", setOf(feb15, mar15), advanceTo = null).isSuccess)

            assertEquals(setOf(feb15, mar15), item().skipped)
            assertEquals(day(2026, 1, 15), item().handledThrough)
        }

    @Test
    fun skip_withAdvanceTo_movesTheCursorInTheSameWrite() =
        runStoreTest {
            repo.saveRecurring(recurringItem(id = "k", handledThrough = day(2026, 1, 15)))

            repo.skipOccurrences("k", setOf(mar15), advanceTo = mar15)

            assertEquals(mar15, item().handledThrough)
            assertEquals(setOf(mar15), item().skipped)
        }

    @Test
    fun skip_persistsAsSortedIsoStrings() =
        runStoreTest {
            repo.saveRecurring(recurringItem(id = "k"))

            repo.skipOccurrences("k", setOf(mar15, feb15), advanceTo = null)

            assertEquals(listOf("2026-02-15", "2026-03-15"), diskDoc().recurring.single().skipped)
        }

    @Test
    fun unskip_removesOnlyTheGivenDates() =
        runStoreTest {
            repo.saveRecurring(recurringItem(id = "k", handledThrough = mar15, skipped = setOf(feb15, mar15)))

            assertTrue(repo.unskipOccurrences("k", setOf(feb15, day(2026, 5, 1))).isSuccess)

            assertEquals(setOf(mar15), item().skipped)
            assertEquals(mar15, item().handledThrough)
        }

    @Test
    fun missingItem_fails_andWritesNothing() =
        runStoreTest {
            assertTrue(repo.skipOccurrences("nope", setOf(mar15), advanceTo = null).isFailure)
            assertTrue(repo.unskipOccurrences("nope", setOf(mar15)).isFailure)

            assertFalse(dataFile.exists())
        }
}
