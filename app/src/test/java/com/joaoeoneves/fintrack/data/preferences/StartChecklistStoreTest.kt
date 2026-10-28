package com.joaoeoneves.fintrack.data.preferences

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartChecklistStoreTest : PreferencesStoreTestBase() {
    private val scope = newScope()
    private val dataStore = newDataStore(scope)
    private val store = StartChecklistStore(dataStore)

    @Test
    fun freshFile_isUndecided() =
        runTest {
            assertNull(store.state.first())
        }

    @Test
    fun set_roundTripsEveryState() =
        runTest {
            StartChecklistState.entries.forEach { state ->
                store.set(state)

                assertEquals(state, store.state.first())
            }
        }

    @Test
    fun set_persistsAcrossStoreInstances() =
        runTest {
            store.set(StartChecklistState.DISMISSED)
            scope.coroutineContext[Job]!!.cancelAndJoin()

            val second = StartChecklistStore(newDataStore(newScope()))

            assertEquals(StartChecklistState.DISMISSED, second.state.first())
        }

    @Test
    fun clear_mapsBackToUndecided() =
        runTest {
            store.set(StartChecklistState.RETIRED)

            store.clear()

            assertNull(store.state.first())
        }

    @Test
    fun clear_onUndecided_isANoOp() =
        runTest {
            store.clear()

            assertNull(store.state.first())
        }

    @Test
    fun unknownRawValue_readsAsUndecided() =
        runTest {
            dataStore.edit { it[stringPreferencesKey("start_checklist_state")] = "KLINGON" }

            assertNull(store.state.first())
        }

    @Test
    fun clear_leavesOtherKeysAlone() =
        runTest {
            val other = stringPreferencesKey("other")
            dataStore.edit { it[other] = "keep" }
            store.set(StartChecklistState.ACTIVE)

            store.clear()

            assertEquals("keep", dataStore.data.first()[other])
        }
}
