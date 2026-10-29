package com.joaoeoneves.fintrack.ui.plan.configure

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import com.joaoeoneves.fintrack.domain.repository.NoPlanSettings
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.ui.plan.ConfigureStatus
import com.joaoeoneves.fintrack.ui.plan.PlanRepositories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The Configure screen's set-up status, measured on the current month of an injected clock. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ConfigureViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val incomeRepo = FakePlannedIncomeRepository()
    private val categoryRepo = FakeCategoryRepository()
    private val envelopeRepo = FakeEnvelopeRepository()
    private val recurringRepo = FakeRecurringRepository()
    private val goalRepo = FakeGoalRepository()
    private val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        ConfigureViewModel(
            PlanRepositories(
                incomeRepo,
                FakeBudgetRepository(),
                categoryRepo,
                FakeMonthSettingsRepository(),
                goalRepo,
                FakeAutomationSettingsRepository(),
            ),
            envelopeRepo,
            recurringRepo,
            NoPlanSettings,
            clock,
        )

    private fun TestScope.statusOf(viewModel: ConfigureViewModel): ConfigureStatus {
        backgroundScope.launch { viewModel.status.collect {} }
        advanceUntilIdle()
        return viewModel.status.value
    }

    private fun line(
        id: String,
        cents: Long,
        frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
    ) = PlannedIncome(
        id,
        id,
        cents,
        0,
        frequency = frequency,
        monthOfYear = if (frequency == IncomeFrequency.MONTHLY) null else 10,
    )

    @Test
    fun initialStatus_isEmpty() =
        runTest(testDispatcher) {
            val empty = ConfigureStatus(0, 0, 0, 0, 0, 0)

            assertEquals(empty, viewModel().status.value)
        }

    @Test
    fun nothingConfigured_hasOnlyTheBuiltInCategories() =
        runTest(testDispatcher) {
            val status = statusOf(viewModel())

            assertEquals(0L, status.incomeMonthlyCents)
            assertEquals(0, status.incomeLineCount)
            assertEquals(0, status.envelopeCount)
            assertEquals(0, status.billCount)
            assertEquals(0L, status.billsMonthlyCents)
            assertEquals(BuiltInCategories.all.count { !it.archived }, status.categoryCount)
        }

    @Test
    fun income_sumsMonthlyLines_excludingExtras() =
        runTest(testDispatcher) {
            incomeRepo.savePlannedIncome(line("a", 100_000L))
            incomeRepo.savePlannedIncome(line("b", 20_000L))
            incomeRepo.savePlannedIncome(line("bonus", 500_000L, IncomeFrequency.YEARLY))
            incomeRepo.savePlannedIncome(line("once", 70_000L, IncomeFrequency.ONCE))

            val status = statusOf(viewModel())

            assertEquals(2, status.incomeLineCount)
            assertEquals(120_000L, status.incomeMonthlyCents)
        }

    @Test
    fun bills_activeExpenseOnly_yearlyDividedByTwelve() =
        runTest(testDispatcher) {
            recurringRepo.saveRecurring(recurringItem("a", amountCents = 10_000L))
            recurringRepo.saveRecurring(
                recurringItem("y", amountCents = 12_000L, frequency = RecurringFrequency.YEARLY),
            )
            recurringRepo.saveRecurring(recurringItem("off", amountCents = 99_000L, active = false))
            recurringRepo.saveRecurring(recurringItem("inc", type = RecurringType.INCOME, amountCents = 77_000L))
            recurringRepo.saveRecurring(
                recurringItem("ended", amountCents = 5_000L, endDate = LocalDate.of(2026, 9, 1)),
            )

            val status = statusOf(viewModel())

            assertEquals(2, status.billCount)
            assertEquals(11_000L, status.billsMonthlyCents)
        }

    @Test
    fun bills_excludeSavingsPlanOwnedItems() =
        runTest(testDispatcher) {
            recurringRepo.saveRecurring(recurringItem("bill", amountCents = 10_000L))
            recurringRepo.saveRecurring(recurringItem("own", amountCents = 50_000L))
            goalRepo.saveGoal(
                SavingsGoal("p", "Plan", 0, GoalTargetType.ANNUAL, LocalDate.of(2026, 1, 1), recurringId = "own"),
            )

            val status = statusOf(viewModel())

            assertEquals(1, status.billCount)
            assertEquals(10_000L, status.billsMonthlyCents)
        }

    @Test
    fun yearlyBillRounding_isToTheNearestCent() =
        runTest(testDispatcher) {
            recurringRepo.saveRecurring(
                recurringItem("y", amountCents = 10_300L, frequency = RecurringFrequency.YEARLY),
            )

            assertEquals(858L, statusOf(viewModel()).billsMonthlyCents)
        }

    @Test
    fun envelopesAndCategories_areCounted_archivedCategoriesNot() =
        runTest(testDispatcher) {
            envelopeRepo.saveEnvelope(EnvelopeEdit("e1", "Food", 1_000L, null, emptySet(), emptyMap()))
            envelopeRepo.saveEnvelope(EnvelopeEdit("e2", "Fun", 2_000L, null, emptySet(), emptyMap()))
            val before = categoryRepo.observeCategories().first().count { !it.archived }
            categoryRepo.setArchived(BuiltInCategories.FOOD.id, true)

            val status = statusOf(viewModel())

            assertEquals(2, status.envelopeCount)
            assertEquals(before - 1, status.categoryCount)
        }

    @Test
    fun status_followsTheClockMonth_forEndedBills() =
        runTest(testDispatcher) {
            recurringRepo.saveRecurring(recurringItem("a", amountCents = 10_000L, endDate = LocalDate.of(2026, 11, 30)))
            val viewModel = viewModel()
            assertEquals(1, statusOf(viewModel).billCount)

            clock.now = Instant.parse("2026-12-15T12:00:00Z")
            recurringRepo.saveRecurring(recurringItem("b", amountCents = 1_000L))
            advanceUntilIdle()

            assertEquals(1, viewModel.status.value.billCount)
            assertEquals(1_000L, viewModel.status.value.billsMonthlyCents)
        }
}
