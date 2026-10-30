package com.joaoeoneves.fintrack.ui.settings

import android.app.Application
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import com.joaoeoneves.fintrack.ui.common.UiText
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
 * [SettingsViewModel.signOut] failure messaging: the `error_sign_out_failed` resource is surfaced
 * as [com.joaoeoneves.fintrack.ui.common.UiText.StringResource] even when the exception carries its
 * own message (the raw message is never shown).
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
    fun signOut_failure_withNonNullMessage_showsSignOutResource_neverRawMessage() =
        runTest(testDispatcher) {
            val authRepo =
                FakeSessionRepository(
                    initialUser = user,
                    nextSignOutResult = Result.failure(IllegalStateException("network unavailable")),
                )
            val viewModel = SettingsViewModel(StaticThemeRepository(), authRepo)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.signOut()
            advanceUntilIdle()

            assertEquals(UiText.StringResource(R.string.error_sign_out_failed), viewModel.uiState.value.signOutError)
        }
}
