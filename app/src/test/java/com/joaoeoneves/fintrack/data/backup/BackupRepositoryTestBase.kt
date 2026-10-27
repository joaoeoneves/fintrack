package com.joaoeoneves.fintrack.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.local.LocalStoreTestBase
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesStore
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Session
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
    protected val restorer by lazy { GuestBackupRestorer(localStore, prefsStore, sessions) }

    @After
    fun cancelScope() {
        scope.cancel()
    }

    protected fun repository(
        expenses: List<Expense> = emptyList(),
        income: List<Income> = emptyList(),
        budgets: List<Budget> = emptyList(),
        categoryDocs: List<Category> = emptyList(),
    ) = BackupRepository(
        FakeExpenseRepository(expenses),
        FakeIncomeRepository(income),
        FakeBudgetRepository(budgets),
        FakeCategoryRepository(categoryDocs),
        prefsStore,
        restorer,
    )
}
