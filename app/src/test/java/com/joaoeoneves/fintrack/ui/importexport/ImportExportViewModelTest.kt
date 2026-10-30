package com.joaoeoneves.fintrack.ui.importexport

import android.app.Application
import android.net.Uri
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.testutil.CancellingContentProvider
import com.joaoeoneves.fintrack.testutil.GenericFailureContentProvider
import com.joaoeoneves.fintrack.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * Unit tests for the export-only [ImportExportViewModel], backed by [FakeExpenseRepository]. Export "files"
 * are real temporary files on disk addressed via `file://` [Uri]s: `ContentResolver.openInputStream`
 * / `openOutputStream` special-case the `file` scheme to open the file directly rather than routing
 * through a `ContentProvider`, so this works under Robolectric's real (shadowed) `Context` without
 * needing to register a fake `ContentProvider`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImportExportViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun context() = RuntimeEnvironment.getApplication()

    private fun expense(
        id: String = "",
        name: String,
        amountCents: Long = 500L,
        category: Category = BuiltInCategories.SHOPPING,
        date: Instant = Instant.parse("2024-01-15T00:00:00Z"),
        note: String? = null,
    ) = Expense(id = id, name = name, amountCents = amountCents, category = category, date = date, note = note)

    // ---- export ----

    @Test
    fun onExportTargetSelected_writesAllExpenses_movesToDoneStateWithCount() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(
                        expense(id = "e1", name = "Coffee"),
                        expense(
                            id = "e2",
                            name = "Rent",
                            amountCents = 150_000L,
                            category = BuiltInCategories.RECURRING,
                        ),
                    ),
                )
            val viewModel = ImportExportViewModel(repo, context())
            val target = File(tempFolder.root, "export.csv")

            viewModel.onExportTargetSelected(Uri.fromFile(target))
            advanceUntilIdle()

            val state = viewModel.exportState.value as ExportUiState.Done
            assertEquals(2, state.exportedCount)
            val writtenCsv = target.readText()
            assertTrue(writtenCsv.contains("Coffee"))
            assertTrue(writtenCsv.contains("Rent"))
        }

    @Test
    fun onExportTargetSelected_emptyRepository_writesHeaderOnly_countZero() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val viewModel = ImportExportViewModel(repo, context())
            val target = File(tempFolder.root, "export.csv")

            viewModel.onExportTargetSelected(Uri.fromFile(target))
            advanceUntilIdle()

            val state = viewModel.exportState.value as ExportUiState.Done
            assertEquals(0, state.exportedCount)
            assertEquals("name,amountCents,category,date,note", target.readText())
        }

    @Test
    fun onDismissExport_resetsStateToIdle() =
        runTest(testDispatcher) {
            val repo = FakeExpenseRepository()
            val viewModel = ImportExportViewModel(repo, context())
            val target = File(tempFolder.root, "export.csv")
            viewModel.onExportTargetSelected(Uri.fromFile(target))
            advanceUntilIdle()
            assertTrue(viewModel.exportState.value is ExportUiState.Done)

            viewModel.onDismissExport()

            assertEquals(ExportUiState.Idle, viewModel.exportState.value)
        }

    // ---- cancellation propagation (must NOT be swallowed into an Error UI state) ----
    //
    // A real android.content.ContentProvider is registered (via Robolectric.setupContentProvider)
    // under a test authority, and a content:// Uri pointing at it is used to select the "file" --
    // this is the supported extension point for making context.contentResolver.openInputStream/
    // openOutputStream throw an arbitrary exception without a mocking library: ContentResolver's
    // own openInputStream/openOutputStream are `final` in the public SDK stub used at compile time,
    // so subclassing/overriding ContentResolver itself doesn't compile.

    @Test
    fun onExportTargetSelected_cancellationDuringFileWrite_doesNotSurfaceAsErrorState() =
        runTest(testDispatcher) {
            Robolectric.setupContentProvider(CancellingContentProvider::class.java, CancellingContentProvider.AUTHORITY)
            val repo = FakeExpenseRepository(listOf(expense(id = "e1", name = "Coffee")))
            val viewModel = ImportExportViewModel(repo, context())

            viewModel.onExportTargetSelected(Uri.parse("content://${CancellingContentProvider.AUTHORITY}/export.csv"))
            advanceUntilIdle()

            // Before the fix, this would surface as ExportUiState.Error. The write attempt happens
            // inside expenseRepository.getAllExpenses().onSuccess { ... }, so the state just before
            // the cancellation point is Exporting -- it must stay there, not flip to Error.
            assertEquals(ExportUiState.Exporting, viewModel.exportState.value)
        }

    @Test
    fun onExportTargetSelected_unwritableTarget_surfacesWriteError() =
        runTest(testDispatcher) {
            Robolectric.setupContentProvider(
                GenericFailureContentProvider::class.java,
                GenericFailureContentProvider.AUTHORITY,
            )
            val repo = FakeExpenseRepository(listOf(expense(id = "e1", name = "Coffee")))
            val viewModel = ImportExportViewModel(repo, context())

            val uri = Uri.parse("content://${GenericFailureContentProvider.AUTHORITY}/export.csv")
            viewModel.onExportTargetSelected(uri)
            advanceUntilIdle()

            assertEquals(
                ExportUiState.Error(UiText.StringResource(R.string.error_export_write_file)),
                viewModel.exportState.value,
            )
        }
}
