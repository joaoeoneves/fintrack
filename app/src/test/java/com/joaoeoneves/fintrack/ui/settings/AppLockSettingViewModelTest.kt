package com.joaoeoneves.fintrack.ui.settings

import com.joaoeoneves.fintrack.data.preferences.AppLockStore
import com.joaoeoneves.fintrack.data.preferences.PreferencesStoreTestBase
import com.joaoeoneves.fintrack.data.security.AppLockController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockSettingViewModelTest : PreferencesStoreTestBase() {
    private var secure = true

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): Pair<AppLockSettingViewModel, AppLockController> {
        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val controller = AppLockController(AppLockStore(newDataStore(newScope())), { secure }, { 0L }, appScope)
        return AppLockSettingViewModel(controller, { secure }) to controller
    }

    private fun TestScope.observed(vm: AppLockSettingViewModel): Job = backgroundScope.launch { vm.uiState.collect {} }

    @Test
    fun unavailableDevice_reportsNotAvailable() =
        runTest {
            secure = false
            val (vm, _) = viewModel()
            observed(vm)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.available)
            assertFalse(vm.uiState.value.enabled)
        }

    @Test
    fun toggleOn_emitsRequest_butDoesNotEnableBeforeSuccess() =
        runTest {
            val (vm, _) = viewModel()
            observed(vm)
            val requests = mutableListOf<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.requestEnableAuth.toList(requests) }
            advanceUntilIdle()

            vm.onToggle(true)
            advanceUntilIdle()

            assertEquals(1, requests.size)
            assertFalse(vm.uiState.value.enabled)
        }

    @Test
    fun authSuccess_enables() =
        runTest {
            val (vm, _) = viewModel()
            observed(vm)

            vm.onToggle(true)
            vm.onEnableAuthResult(true)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.enabled)
        }

    @Test
    fun authCancelled_staysOff() =
        runTest {
            val (vm, _) = viewModel()
            observed(vm)

            vm.onToggle(true)
            vm.onEnableAuthResult(false)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.enabled)
        }

    @Test
    fun toggleOff_disablesWithoutAuthRequest() =
        runTest {
            val (vm, _) = viewModel()
            observed(vm)
            val requests = mutableListOf<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.requestEnableAuth.toList(requests) }
            vm.onEnableAuthResult(true)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.enabled)

            vm.onToggle(false)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.enabled)
            assertTrue(requests.isEmpty())
        }

    @Test
    fun refreshAvailability_picksUpChange() =
        runTest {
            secure = false
            val (vm, _) = viewModel()
            observed(vm)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.available)

            secure = true
            vm.refreshAvailability()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.available)
        }
}
