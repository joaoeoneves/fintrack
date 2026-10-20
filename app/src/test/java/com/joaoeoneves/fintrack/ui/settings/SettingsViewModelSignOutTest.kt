package com.joaoeoneves.fintrack.ui.settings

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeAuthRepository
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import com.joaoeoneves.fintrack.testutil.FakeCurrencyRepository
import com.joaoeoneves.fintrack.testutil.FakeStringContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SettingsViewModel.signOut] failure messaging with a real (stubbed) Context: the localized
 * `error_sign_out_failed` string is shown even when the exception carries its own message. Needs
 * Robolectric for [FakeStringContext]'s `Resources` construction, unlike the plain-JVM
 * [SettingsViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsViewModelSignOutTest {
    private val testDispatcher = StandardTestDispatcher()

    private val user = AuthUser(uid = "uid-1", displayName = "Ada", email = "ada@example.com", photoUrl = null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class StaticThemeRepository : ThemeRepository {
        private val flow = MutableStateFlow(ThemePreference.SYSTEM)

        override fun observeThemePreference(): Flow<ThemePreference> = flow

        override suspend fun setThemePreference(preference: ThemePreference) {
            flow.value = preference
        }
    }

    @Test
    fun signOut_failure_withNonNullMessage_andContext_showsLocalizedString_neverRawMessage() =
        runTest(testDispatcher) {
            val authRepo =
                FakeAuthRepository(
                    initialUser = user,
                    nextSignOutResult = Result.failure(IllegalStateException("network unavailable")),
                )
            val context = FakeStringContext(R.string.error_sign_out_failed, "translated sign-out failure")
            val viewModel =
                SettingsViewModel(
                    StaticThemeRepository(),
                    FakeCurrencyRepository(CurrencyOption.USD),
                    authRepo,
                    context = context,
                )
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.signOut()
            advanceUntilIdle()

            assertEquals("translated sign-out failure", viewModel.uiState.value.signOutError)
        }

    @Test
    fun signOut_failure_withNonNullMessage_andNoContext_showsEnglishFallback() =
        runTest(testDispatcher) {
            val authRepo =
                FakeAuthRepository(
                    initialUser = user,
                    nextSignOutResult = Result.failure(IllegalStateException("network unavailable")),
                )
            val currencyRepo = FakeCurrencyRepository(CurrencyOption.USD)
            val viewModel = SettingsViewModel(StaticThemeRepository(), currencyRepo, authRepo)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.signOut()
            advanceUntilIdle()

            assertEquals("Sign-out failed", viewModel.uiState.value.signOutError)
        }
}
