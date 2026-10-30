package com.joaoeoneves.fintrack.data.preferences

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

// Robolectric only so android.util.Log (called on the error path) is real.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppLockStoreIoErrorTest {
    @Test
    fun ioException_readsAsOff() =
        runTest {
            val broken =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> = flow { throw IOException("boom") }

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                        throw IOException("boom")
                }
            val failing = AppLockStore(broken)

            assertEquals(false, failing.enabled.first())
            failing.setEnabled(true)
        }
}
