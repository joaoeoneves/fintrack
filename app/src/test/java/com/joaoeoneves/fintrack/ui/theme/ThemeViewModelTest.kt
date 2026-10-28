package com.joaoeoneves.fintrack.ui.theme

import com.joaoeoneves.fintrack.domain.model.MotionPreference
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.MotionRepository
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ThemeViewModel], backed by a local [FakeThemeRepository] defined at the bottom
 * of this file (mirroring the pattern used by other ViewModel tests in this codebase, e.g.
 * `AuthViewModelTest`'s `GatedSignInAuthRepository`, for a repository with no existing fake under
 * `app/src/main`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun themePreference_initialValue_isNull() =
        runTest(testDispatcher) {
            val repo = FakeThemeRepository(ThemePreference.LIGHT)
            val viewModel = ThemeViewModel(repo, FakeMotionRepository())

            // Before advanceUntilIdle/collection, the StateFlow built via stateIn(WhileSubscribed)
            // has not yet started collecting upstream, so it should still report its initialValue
            // regardless of what the repository is actually seeded with (null = not yet loaded).
            assertNull(viewModel.themePreference.value)
        }

    @Test
    fun themePreference_afterCollection_reflectsRepositoryValue() =
        runTest(testDispatcher) {
            val repo = FakeThemeRepository(ThemePreference.DARK)
            val viewModel = ThemeViewModel(repo, FakeMotionRepository())

            // stateIn(WhileSubscribed) only starts collecting the upstream flow once there is an
            // active subscriber -- reading `.value` alone never triggers that, so force one here.
            val job = launch { viewModel.themePreference.collect {} }
            advanceUntilIdle()

            assertEquals(ThemePreference.DARK, viewModel.themePreference.value)
            job.cancel()
        }

    @Test
    fun toggleTheme_currentlyResolvedDark_setsPreferenceToLight() =
        runTest(testDispatcher) {
            val repo = FakeThemeRepository(ThemePreference.SYSTEM)
            val viewModel = ThemeViewModel(repo, FakeMotionRepository())
            advanceUntilIdle()

            viewModel.toggleTheme(currentlyResolvedDark = true)
            advanceUntilIdle()

            assertEquals(ThemePreference.LIGHT, repo.currentPreference())
        }

    @Test
    fun toggleTheme_currentlyResolvedLight_setsPreferenceToDark() =
        runTest(testDispatcher) {
            val repo = FakeThemeRepository(ThemePreference.SYSTEM)
            val viewModel = ThemeViewModel(repo, FakeMotionRepository())
            advanceUntilIdle()

            viewModel.toggleTheme(currentlyResolvedDark = false)
            advanceUntilIdle()

            assertEquals(ThemePreference.DARK, repo.currentPreference())
        }

    @Test
    fun toggleTheme_ignoresStoredPreference_onlyDependsOnResolvedFlagArgument() =
        runTest(testDispatcher) {
            // Even though the stored preference is explicitly LIGHT, toggling with
            // currentlyResolvedDark = true (as if the resolved theme were dark via some other
            // path) must still flip to LIGHT -- the method deliberately takes the caller's
            // resolved-state snapshot, not the stored preference, as its source of truth.
            val repo = FakeThemeRepository(ThemePreference.LIGHT)
            val viewModel = ThemeViewModel(repo, FakeMotionRepository())
            advanceUntilIdle()

            viewModel.toggleTheme(currentlyResolvedDark = true)
            advanceUntilIdle()

            assertEquals(ThemePreference.LIGHT, repo.currentPreference())
        }

    @Test
    fun toggleTheme_calledTwice_toggledBackAndForthByCaller_endsAtSecondCallsTarget() =
        runTest(testDispatcher) {
            val repo = FakeThemeRepository(ThemePreference.SYSTEM)
            val viewModel = ThemeViewModel(repo, FakeMotionRepository())
            advanceUntilIdle()

            viewModel.toggleTheme(currentlyResolvedDark = false) // -> DARK
            advanceUntilIdle()
            assertEquals(ThemePreference.DARK, repo.currentPreference())

            viewModel.toggleTheme(currentlyResolvedDark = true) // -> LIGHT
            advanceUntilIdle()
            assertEquals(ThemePreference.LIGHT, repo.currentPreference())
        }

    @Test
    fun motionPreference_initialValue_isSystem() =
        runTest(testDispatcher) {
            val viewModel = ThemeViewModel(FakeThemeRepository(), FakeMotionRepository(MotionPreference.ON))

            assertEquals(MotionPreference.SYSTEM, viewModel.motionPreference.value)
        }

    @Test
    fun motionPreference_afterCollection_reflectsRepositoryAndUpdates() =
        runTest(testDispatcher) {
            val motion = FakeMotionRepository(MotionPreference.ON)
            val viewModel = ThemeViewModel(FakeThemeRepository(), motion)
            val job = launch { viewModel.motionPreference.collect {} }
            advanceUntilIdle()
            assertEquals(MotionPreference.ON, viewModel.motionPreference.value)

            motion.setMotionPreference(MotionPreference.OFF)
            advanceUntilIdle()
            assertEquals(MotionPreference.OFF, viewModel.motionPreference.value)
            job.cancel()
        }

    private class FakeMotionRepository(
        initial: MotionPreference = MotionPreference.SYSTEM,
    ) : MotionRepository {
        private val flow = MutableStateFlow(initial)

        override fun observeMotionPreference(): Flow<MotionPreference> = flow

        override suspend fun setMotionPreference(preference: MotionPreference) {
            flow.value = preference
        }
    }

    /**
     * Minimal in-memory [ThemeRepository] fake, local to this test file since no fake exists
     * under `app/src/main` for this repository.
     */
    private class FakeThemeRepository(
        initial: ThemePreference = ThemePreference.SYSTEM,
    ) : ThemeRepository {
        private val preferenceFlow = MutableStateFlow(initial)

        override fun observeThemePreference(): Flow<ThemePreference> = preferenceFlow

        override suspend fun setThemePreference(preference: ThemePreference) {
            preferenceFlow.value = preference
        }

        fun currentPreference(): ThemePreference = preferenceFlow.value
    }
}
