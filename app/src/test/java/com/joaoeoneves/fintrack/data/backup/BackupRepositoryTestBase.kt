package com.joaoeoneves.fintrack.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlaceholderNames
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.FakeSuggestionRepository
import com.joaoeoneves.fintrack.data.local.LocalStoreTestBase
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesStore
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.testutil.FakeAccountRestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import java.io.File

/** Wires a [BackupRepository] over fake data repositories and real guest/preferences stores on temp files. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BackupRepositoryTestBase : LocalStoreTestBase() {
    private val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())

    protected val prefsStore =
        UserPreferencesStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "p.preferences_pb") }),
        )

    protected val sessions = FakeSessionRepository(initialSession = Session.Guest)
    protected val localStore by lazy { newStore() }
    protected val gate = RestoreGate()

    protected val account = FakeAccountRestore()
    protected val restorer by lazy {
        BackupRestorer(GuestBackupRestorer(localStore, prefsStore, sessions), account, sessions, gate)
    }

    @After
    fun cancelScope() {
        scope.cancel()
    }

    @Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
    protected fun repository(
        expenses: List<Expense> = emptyList(),
        income: List<Income> = emptyList(),
        budgets: List<Budget> = emptyList(),
        categoryDocs: List<Category> = emptyList(),
        plannedIncome: List<PlannedIncome> = emptyList(),
        recurring: List<RecurringItem> = emptyList(),
        monthSettings: List<MonthSettings> = emptyList(),
        groups: List<Envelope> = emptyList(),
        salaryProfile: SalaryProfile? = null,
        goals: List<SavingsGoal> = emptyList(),
        automation: AutomationSettings = AutomationSettings(),
        deletedCategoryName: String = "Deleted category",
        dismissed: Set<String> = emptySet(),
    ) = BackupRepository(
        FakeExpenseRepository(expenses),
        FakeIncomeRepository(income),
        PlanBackupSource(
            FakeBudgetRepository(budgets),
            FakePlannedIncomeRepository(plannedIncome),
            FakeRecurringRepository(recurring),
            FakeMonthSettingsRepository(monthSettings),
            FakeEnvelopeRepository(groups),
            PlanSingletonSources(
                FakeSalaryProfileRepository(salaryProfile),
                FakeGoalRepository(goals),
                FakeAutomationSettingsRepository(automation),
                FakePlaceholderNames(deletedCategoryName),
            ),
        ),
        FakeCategoryRepository(categoryDocs),
        prefsStore,
        restorer,
        suggestionRepository = FakeSuggestionRepository(dismissed),
    )
}

/** The guest restore through [BackupRepository.restore]: success, or the failure's cause. */
suspend fun BackupRepository.restoreAsGuest(document: BackupDocument): Result<Unit> =
    when (val result = restore(document)) {
        AccountRestoreResult.Success -> Result.success(Unit)
        is AccountRestoreResult.Failed -> Result.failure(result.cause)
        else -> Result.failure(IllegalStateException("Unexpected result $result"))
    }
