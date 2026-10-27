package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.joaoeoneves.fintrack.data.preferences.PreferencesStoreTestBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigrationOfferStoreTest : PreferencesStoreTestBase() {
    private class BrokenDataStore(
        private val error: Throwable,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw error }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw error
    }

    @Test
    fun freshStore_handledRevisionIsNull() =
        runTest {
            assertNull(MigrationOfferStore(newDataStore(newScope())).handledRevision.first())
        }

    @Test
    fun markHandled_persistsAndOverwrites() =
        runTest {
            val store = MigrationOfferStore(newDataStore(newScope()))

            assertTrue(store.markHandled(4).isSuccess)
            assertEquals(4L, store.handledRevision.first())
            store.markHandled(9)
            assertEquals(9L, store.handledRevision.first())
        }

    @Test
    fun markHandled_zeroRevision_isDistinctFromNever() =
        runTest {
            val store = MigrationOfferStore(newDataStore(newScope()))

            store.markHandled(0)

            assertEquals(0L, store.handledRevision.first())
        }

    @Test
    fun value_survivesNewStoreOnSameFile() =
        runTest {
            val first = newScope()
            MigrationOfferStore(newDataStore(first)).markHandled(7)
            first.coroutineContext[kotlinx.coroutines.Job]?.cancel()

            val reopened = PreferenceDataStoreFactory.create(scope = newScope(), produceFile = { file })

            assertEquals(7L, MigrationOfferStore(reopened).handledRevision.first())
        }

    @Test
    fun ioExceptionOnRead_emitsNull() =
        runTest {
            val store = MigrationOfferStore(BrokenDataStore(IOException("disk")))

            assertNull(store.handledRevision.first())
        }

    @Test
    fun nonIoExceptionOnRead_propagates() =
        runTest {
            val store = MigrationOfferStore(BrokenDataStore(IllegalStateException("bug")))

            val thrown = runCatching { store.handledRevision.first() }.exceptionOrNull()

            assertTrue(thrown is IllegalStateException)
        }

    @Test
    fun ioExceptionOnWrite_isFailureResult() =
        runTest {
            val error = IOException("full")
            val result = MigrationOfferStore(BrokenDataStore(error)).markHandled(1)

            assertSame(error, result.exceptionOrNull())
        }
}
