package com.joaoeoneves.fintrack.ui.settings

import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import com.joaoeoneves.fintrack.testutil.FakeCurrencyRepository
import com.joaoeoneves.fintrack.testutil.FakeLanguageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** [SettingsViewModel] language state and [SettingsViewModel.setLanguage]. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelLanguageTest {
    private val testDispatcher = StandardTestDispatcher()

    private class StaticThemeRepository : ThemeRepository {
        override fun observeThemePreference(): Flow<ThemePreference> = flowOf(ThemePreference.SYSTEM)

        override suspend fun setThemePreference(preference: ThemePreference) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(languages: FakeLanguageRepository?) =
        SettingsViewModel(
            StaticThemeRepository(),
            FakeCurrencyRepository(CurrencyOption.EUR),
            FakeSessionRepository(initialUser = null),
            languages,
        )

    private fun TestScope.collect(vm: SettingsViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun language_reflectsRepositoryValue() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeLanguageRepository(AppLanguage.ENGLISH))
            collect(vm)

            assertEquals(AppLanguage.ENGLISH, vm.uiState.value.language)
        }

    @Test
    fun language_beforeCollection_isDefaultSystem() =
        runTest(testDispatcher) {
            val vm = viewModel(FakeLanguageRepository(AppLanguage.ENGLISH))

            assertEquals(AppLanguage.SYSTEM, vm.uiState.value.language)
        }

    @Test
    fun language_followsExternalChanges() =
        runTest(testDispatcher) {
            val repo = FakeLanguageRepository(AppLanguage.SYSTEM)
            val vm = viewModel(repo)
            collect(vm)

            repo.emit(AppLanguage.PORTUGUESE)
            advanceUntilIdle()

            assertEquals(AppLanguage.PORTUGUESE, vm.uiState.value.language)
        }

    @Test
    fun setLanguage_delegatesToRepository_andUiStateUpdates() =
        runTest(testDispatcher) {
            val repo = FakeLanguageRepository(AppLanguage.SYSTEM)
            val vm = viewModel(repo)
            collect(vm)

            vm.setLanguage(AppLanguage.ENGLISH)
            advanceUntilIdle()

            assertEquals(listOf(AppLanguage.ENGLISH), repo.setCalls)
            assertEquals(AppLanguage.ENGLISH, vm.uiState.value.language)
        }

    @Test
    fun setLanguage_eachValue_isForwardedInOrder() =
        runTest(testDispatcher) {
            val repo = FakeLanguageRepository()
            val vm = viewModel(repo)
            collect(vm)

            AppLanguage.entries.forEach { vm.setLanguage(it) }
            advanceUntilIdle()

            assertEquals(AppLanguage.entries.toList(), repo.setCalls)
        }

    @Test
    fun noLanguageRepository_defaultsToSystem_andSetLanguageIsNoOp() =
        runTest(testDispatcher) {
            val vm = viewModel(null)
            collect(vm)

            vm.setLanguage(AppLanguage.ENGLISH)
            advanceUntilIdle()

            assertEquals(AppLanguage.SYSTEM, vm.uiState.value.language)
        }
}
