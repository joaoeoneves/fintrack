package com.joaoeoneves.fintrack.data.security

import com.joaoeoneves.fintrack.data.preferences.AppLockStore
import com.joaoeoneves.fintrack.data.preferences.PreferencesStoreTestBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockControllerTest : PreferencesStoreTestBase() {
    private var secure = true
    private var now = 10_000L
    private val store = AppLockStore(newDataStore(newScope()))

    private fun TestScope.controller(enabled: Boolean): AppLockController {
        if (enabled) {
            // Seed through the store before the controller exists (UnconfinedTestDispatcher store scope).
            runBlocking { store.setEnabled(true) }
        }
        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        return AppLockController(store, { secure }, { now }, appScope)
    }

    private fun TestScope.ready(enabled: Boolean): AppLockController = controller(enabled).also { advanceUntilIdle() }

    @Test
    fun beforeStoreRead_isUnknown() =
        runTest {
            val c = controller(enabled = true)

            assertEquals(LockState.Unknown, c.state.value)
        }

    @Test
    fun coldStart_enabledAndSecure_locks() = runTest { assertEquals(LockState.Locked, ready(true).state.value) }

    @Test
    fun coldStart_disabled_unlocked() = runTest { assertEquals(LockState.Unlocked, ready(false).state.value) }

    @Test
    fun coldStart_enabledButNotSecure_unlocked() =
        runTest {
            secure = false

            assertEquals(LockState.Unlocked, ready(true).state.value)
        }

    private fun AppLockController.unlockedAfterColdStart() = also { onAuthenticated() }

    @Test
    fun backgroundUnderTimeout_staysUnlocked() =
        runTest {
            val c = ready(true).unlockedAfterColdStart()
            c.onBackground()
            now += 59_999L
            c.onForeground()

            assertEquals(LockState.Unlocked, c.state.value)
        }

    @Test
    fun backgroundOverTimeout_locks() =
        runTest {
            val c = ready(true).unlockedAfterColdStart()
            c.onBackground()
            now += 60_000L
            c.onForeground()

            assertEquals(LockState.Locked, c.state.value)
        }

    @Test
    fun backgroundOverTimeout_whileDisabled_staysUnlocked() =
        runTest {
            val c = ready(false)
            c.onBackground()
            now += 120_000L
            c.onForeground()

            assertEquals(LockState.Unlocked, c.state.value)
        }

    @Test
    fun foregroundWithoutBackground_doesNotLock() =
        runTest {
            val c = ready(true).unlockedAfterColdStart()
            c.onForeground()

            assertEquals(LockState.Unlocked, c.state.value)
        }

    @Test
    fun lifecycleIgnoredWhileAuthenticating() =
        runTest {
            val c = ready(true).unlockedAfterColdStart()
            c.onAuthenticationStarted()
            c.onBackground()
            now += 120_000L
            c.onForeground()

            assertEquals(LockState.Unlocked, c.state.value)
        }

    @Test
    fun authenticationEnded_clearsPendingBackgroundTimestamp() =
        runTest {
            val c = ready(true).unlockedAfterColdStart()
            c.onBackground()
            c.onAuthenticationStarted()
            c.onAuthenticationEnded()
            now += 120_000L
            c.onForeground()

            assertEquals(LockState.Unlocked, c.state.value)
        }

    @Test
    fun lifecycleWorksAgainAfterAuthenticationEnded() =
        runTest {
            val c = ready(true).unlockedAfterColdStart()
            c.onAuthenticationStarted()
            c.onAuthenticationEnded()
            c.onBackground()
            now += 60_000L
            c.onForeground()

            assertEquals(LockState.Locked, c.state.value)
        }

    @Test
    fun enableAtRuntime_staysUnlocked() =
        runTest {
            val c = ready(false)

            c.setEnabled(true)
            advanceUntilIdle()

            assertEquals(LockState.Unlocked, c.state.value)
            assertEquals(true, c.enabled.value)
        }

    @Test
    fun enabledAtRuntime_thenLongBackground_locks() =
        runTest {
            val c = ready(false)
            c.setEnabled(true)
            advanceUntilIdle()
            c.onBackground()
            now += 61_000L
            c.onForeground()

            assertEquals(LockState.Locked, c.state.value)
        }

    @Test
    fun disable_unlocksAndPersists() =
        runTest {
            val c = ready(true)
            assertEquals(LockState.Locked, c.state.value)

            c.setEnabled(false)
            advanceUntilIdle()

            assertEquals(LockState.Unlocked, c.state.value)
            assertEquals(false, store.enabled.first())
        }

    @Test
    fun onUnavailable_unlocks() =
        runTest {
            val c = ready(true)

            c.onUnavailable()

            assertEquals(LockState.Unlocked, c.state.value)
        }

    @Test
    fun onAuthenticated_unlocks() =
        runTest {
            val c = ready(true)

            c.onAuthenticated()

            assertEquals(LockState.Unlocked, c.state.value)
        }
}
