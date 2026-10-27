package com.joaoeoneves.fintrack.ui.migration

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.backup.BackupCounts
import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.LocalDataStore
import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalStoreTestBase
import com.joaoeoneves.fintrack.data.migration.AccountMigrator
import com.joaoeoneves.fintrack.data.migration.MigrationOfferStore
import com.joaoeoneves.fintrack.data.migration.MigrationResult
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Scripted [AccountMigrator]: each call reports [progress], optionally awaits [gate], returns the next result. */
class FakeAccountMigrator(
    var results: MutableList<MigrationResult> = mutableListOf(),
    var progress: List<Pair<Int, Int>> = emptyList(),
    var gate: CompletableDeferred<Unit>? = null,
) : AccountMigrator {
    val received = mutableListOf<LocalDataDocument>()

    override suspend fun migrate(
        local: LocalDataDocument,
        onProgress: (written: Int, total: Int) -> Unit,
    ): MigrationResult {
        received += local
        progress.forEach { (written, total) -> onProgress(written, total) }
        gate?.await()
        return results.removeAt(0)
    }
}

/** Shared wiring: signed-in session, real [LocalDataStore] on a temp folder, real DataStore offer store. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
abstract class LocalDataMigrationViewModelTestBase : LocalStoreTestBase() {
    protected val user = AuthUser("u", null, null, null)
    protected val sessions = FakeSessionRepository(initialUser = user)
    protected val migrator = FakeAccountMigrator()
    protected val store: LocalDataStore by lazy { newStore() }
    private val offerScope = CoroutineScope(dispatcher + Job())
    protected val offerStore: MigrationOfferStore by lazy {
        MigrationOfferStore(
            PreferenceDataStoreFactory.create(
                scope = offerScope,
                produceFile = { File(tmp.root, "offer.preferences_pb") },
            ),
        )
    }

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        offerScope.cancel()
    }

    protected fun viewModel() = LocalDataMigrationViewModel(sessions, store, offerStore, migrator, dispatcher)

    protected suspend fun seedLocal(
        expenses: Int = 2,
        budgets: Int = 1,
    ): LocalDataDocument {
        store.update {
            it.copy(
                expenses = (1..expenses).map { n -> LocalExpense("e$n", "Item", 100, "FOOD", "2026-03-10T12:00:00Z") },
                budgets = listOf(LocalBudget("FOOD", 100)).take(budgets),
            ) to Unit
        }
        return store.data.first()
    }

    protected fun TestScope.settle() = advanceUntilIdle()

    protected fun ok(
        written: Int,
        total: Int = written,
        skipped: Int = 0,
    ) = MigrationResult(written, total, skipped)

    protected fun failed(
        written: Int,
        total: Int,
        error: Throwable,
    ) = MigrationResult(written, total, 0, error)

    protected fun counts(
        expenses: Int,
        income: Int = 0,
        budgets: Int = 0,
        categories: Int = 0,
    ) = BackupCounts(expenses, income, budgets, categories)

    protected fun assertText(
        expectedRes: Int,
        message: UiText,
    ) = assertEquals(UiText.StringResource(expectedRes), message)
}
