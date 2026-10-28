package com.joaoeoneves.fintrack.ui.plan.salary.setup

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.usecase.ManageSalaryProfile
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

/** Shared fixtures for the [SalarySetupViewModel] tests. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
abstract class SalarySetupViewModelTestBase {
    protected val dispatcher = StandardTestDispatcher()
    protected val profiles = FakeSalaryProfileRepository()
    protected val income = FakePlannedIncomeRepository()
    protected val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    protected val labels = SalaryLineLabels()

    @Before
    fun setUpMain() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDownMain() = Dispatchers.resetMain()

    /** A net-mode form with both lump sums typed. */
    protected fun netForm(): SalaryForm {
        val lumps = 50_000L to 60_000L
        return SalaryForm(
            mode = SalaryInputMode.NET,
            netMonthly = 90_000L,
            netHoliday = lumps.first,
            netChristmas = lumps.second,
        )
    }

    protected fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        profileRepo: FakeSalaryProfileRepository = profiles,
        incomeRepo: FakePlannedIncomeRepository = income,
    ) = SalarySetupViewModel(handle, profileRepo, ManageSalaryProfile(profileRepo, incomeRepo), incomeRepo, clock)

    /** Starts collecting state and events so the view model is live; returns the events seen so far, live. */
    protected fun TestScope.started(vm: SalarySetupViewModel): MutableList<SalarySetupEvent> {
        val events = mutableListOf<SalarySetupEvent>()
        backgroundScope.launch { vm.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventFlow.collect { events += it } }
        advanceUntilIdle()
        return events
    }
}
