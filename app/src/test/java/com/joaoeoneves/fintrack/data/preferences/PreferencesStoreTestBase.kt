package com.joaoeoneves.fintrack.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

/** Shared setup: real Preferences DataStore on a temp file (not pre-created). */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class PreferencesStoreTestBase {
    @get:Rule
    val tmp = TemporaryFolder()

    protected val file: File by lazy { File(tmp.root, "t.preferences_pb") }

    protected fun newScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher() + Job())

    protected fun newDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
}
