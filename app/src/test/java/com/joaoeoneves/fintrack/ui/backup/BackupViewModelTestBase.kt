package com.joaoeoneves.fintrack.ui.backup

import android.app.Application
import android.net.Uri
import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlaceholderNames
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.data.backup.BackupCodec
import com.joaoeoneves.fintrack.data.backup.BackupDocument
import com.joaoeoneves.fintrack.data.backup.BackupRepository
import com.joaoeoneves.fintrack.data.backup.BackupRepositoryTestBase
import com.joaoeoneves.fintrack.data.backup.PlanBackupSource
import com.joaoeoneves.fintrack.data.backup.PlanSingletonSources
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.testutil.MutableClock
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/** Shared wiring for [BackupViewModel] tests: real stores, file:// Uris, test dispatcher for Main and IO. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
abstract class BackupViewModelTestBase : BackupRepositoryTestBase() {
    protected val clock = MutableClock(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    protected fun viewModel(
        expenses: List<Expense> = emptyList(),
        repository: BackupRepository? = null,
    ) = BackupViewModel(
        repository ?: repository(expenses = expenses),
        RuntimeEnvironment.getApplication(),
        dispatcher,
        clock,
    )

    protected fun failingRepository() =
        BackupRepository(
            FakeExpenseRepository(),
            FakeIncomeRepository(),
            PlanBackupSource(
                FakePlannedIncomeRepository(nextObserveError = IllegalStateException("boom")),
                FakeRecurringRepository(),
                FakeMonthSettingsRepository(),
                FakeEnvelopeRepository(),
                PlanSingletonSources(
                    FakeSalaryProfileRepository(),
                    FakeAutomationSettingsRepository(),
                    FakePlaceholderNames(),
                ),
            ),
            FakeCategoryRepository(),
            prefsStore,
            restorer,
        )

    protected fun fileWith(
        text: String,
        name: String = "backup.json",
    ): Uri = Uri.fromFile(tmp.newFile(name).apply { writeText(text) })

    protected fun newFileUri(name: String = "out.json"): Pair<File, Uri> {
        val file = tmp.newFile(name)
        return file to Uri.fromFile(file)
    }

    protected fun encode(doc: BackupDocument): String = BackupCodec.encode(doc)

    protected fun TestScope.settle() = advanceUntilIdle()

    protected fun assertError(
        expectedRes: Int,
        message: UiText,
    ) = assertEquals(UiText.StringResource(expectedRes), message)
}
