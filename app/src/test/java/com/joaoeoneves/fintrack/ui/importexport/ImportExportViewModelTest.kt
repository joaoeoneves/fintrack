package com.joaoeoneves.fintrack.ui.importexport

import android.app.Application
import android.net.Uri
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.testutil.CancellingContentProvider
import com.joaoeoneves.fintrack.testutil.FakeStringContext
import com.joaoeoneves.fintrack.testutil.GenericFailureContentProvider
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
 * Unit tests for [ImportExportViewModel], backed by [FakeExpenseRepository]. Import/export "files"
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
        category: ExpenseCategory = ExpenseCategory.SHOPPING,
        date: Instant = Instant.parse("2024-01-15T00:00:00Z"),
        note: String? = null,
    ) = Expense(id = id, name = name, amountCents = amountCents, category = category, date = date, note = note)

    // ---- import ----

    @Test
    fun onImportFileSelected_validCsv_movesToPreviewState_withParsedExpenses() =
        runTest(testDispatcher) {
            val csv =
                """
                name,amountCents,category,date,note
                Coffee,500,Shopping,2024-01-15T00:00:00Z,with milk
                Rent,150000,Recurring,2024-02-01,
                """.trimIndent()
            val file = tempFolder.newFile("import.csv").apply { writeText(csv) }
            val repo = FakeExpenseRepository()
            val viewModel = ImportExportViewModel(repo, context())

            viewModel.onImportFileSelected(Uri.fromFile(file))
            advanceUntilIdle()

            val state = viewModel.importState.value as ImportUiState.Preview
            assertEquals(2, state.validExpenses.size)
            assertTrue(state.failures.isEmpty())
            assertEquals("Coffee", state.validExpenses[0].name)
            assertEquals("Rent", state.validExpenses[1].name)
        }

    @Test
    fun onImportFileSelected_csvWithSomeBadRows_previewIncludesFailures() =
        runTest(testDispatcher) {
            val csv =
                """
                name,amountCents,category,date,note
                Coffee,500,Shopping,2024-01-15T00:00:00Z,
                Bad,notanumber,Shopping,2024-01-15T00:00:00Z,
                """.trimIndent()
            val file = tempFolder.newFile("import.csv").apply { writeText(csv) }
            val repo = FakeExpenseRepository()
            val viewModel = ImportExportViewModel(repo, context())

            viewModel.onImportFileSelected(Uri.fromFile(file))
            advanceUntilIdle()

            val state = viewModel.importState.value as ImportUiState.Preview
            assertEquals(1, state.validExpenses.size)
            assertEquals(1, state.failures.size)
        }

    @Test
    fun onImportFileSelected_emptyFile_movesToErrorState() =
        runTest(testDispatcher) {
            val file = tempFolder.newFile("empty.csv")
            val repo = FakeExpenseRepository()
            val viewModel = ImportExportViewModel(repo, context())

            viewModel.onImportFileSelected(Uri.fromFile(file))
            advanceUntilIdle()

            assertTrue(viewModel.importState.value is ImportUiState.Error)
        }

    @Test
    fun onImportFileSelected_missingFile_movesToErrorState_doesNotCrash() =
        runTest(testDispatcher) {
            // Real note on why this uses FakeStringContext instead of context() directly: this
            // ViewModel's Context param is required (not nullable), and the failure branch under
            // test calls context.getString(R.string.error_import_read_file). This project's
            // Robolectric setup does not currently have `android.testOptions.unitTests
            // .isIncludeAndroidResources = true` set in app/build.gradle.kts, so a *real* Context's
            // getString() throws `Resources.NotFoundException` for every resource id -- including
            // R.string.app_name -- reproducible even on main before this feature branch (confirmed
            // via a throwaway probe test), i.e. this is a pre-existing test-infrastructure gap, not
            // a regression from this feature and not a production bug in ImportExportViewModel.kt.
            // FakeStringContext wraps the real Robolectric context() (so ContentResolver/file access
            // for openInputStream still works normally) while serving a canned value for getString,
            // giving this test real, working coverage of the exception -> Error-state path without
            // depending on that broken resource-loading pipeline.
            val missing = File(tempFolder.root, "does-not-exist.csv")
            val repo = FakeExpenseRepository()
            val fakeContext =
                FakeStringContext(
                    R.string.error_import_read_file,
                    "Could not read the selected file",
                    base = context(),
                )
            val viewModel = ImportExportViewModel(repo, fakeContext)

            viewModel.onImportFileSelected(Uri.fromFile(missing))
            advanceUntilIdle()

            val state = viewModel.importState.value
            assertTrue(state is ImportUiState.Error)
            assertEquals("Could not read the selected file", (state as ImportUiState.Error).message)
        }

    @Test
    fun onConfirmImport_afterPreview_movesToDoneState_andRepositoryContainsNewExpenses() =
        runTest(testDispatcher) {
            val csv =
                """
                name,amountCents,category,date,note
                Coffee,500,Shopping,2024-01-15T00:00:00Z,
                Rent,150000,Recurring,2024-02-01,
                """.trimIndent()
            val file = tempFolder.newFile("import.csv").apply { writeText(csv) }
            val repo = FakeExpenseRepository()
            val viewModel = ImportExportViewModel(repo, context())
            viewModel.onImportFileSelected(Uri.fromFile(file))
            advanceUntilIdle()

            viewModel.onConfirmImport()
            advanceUntilIdle()

            val state = viewModel.importState.value as ImportUiState.Done
            assertEquals(2, state.importedCount)
            val stored = repo.getAllExpenses().getOrThrow()
            assertEquals(2, stored.size)
            assertTrue(stored.all { it.id.isNotBlank() })
            assertEquals(setOf("Coffee", "Rent"), stored.map { it.name }.toSet())
        }

    @Test
    fun onConfirmImport_appendsToExistingExpenses_doesNotReplaceThem() =
        runTest(testDispatcher) {
            val existing = expense(id = "existing", name = "Existing")
            val csv = "name,amountCents,category,date,note\nNew,100,Shopping,2024-01-01,\n"
            val file = tempFolder.newFile("import.csv").apply { writeText(csv) }
            val repo = FakeExpenseRepository(listOf(existing))
            val viewModel = ImportExportViewModel(repo, context())
            viewModel.onImportFileSelected(Uri.fromFile(file))
            advanceUntilIdle()

            viewModel.onConfirmImport()
            advanceUntilIdle()

            val stored = repo.getAllExpenses().getOrThrow()
            assertEquals(2, stored.size)
            assertTrue(stored.any { it.name == "Existing" })
            assertTrue(stored.any { it.name == "New" })
        }

    // ---- partial-failure bulk import ----

    @Test
    fun onConfirmImport_partialFailure_movesToPartialFailureState_withMatchingStoredCount() =
        runTest(testDispatcher) {
            val csv =
                """
                name,amountCents,category,date,note
                Coffee,500,Shopping,2024-01-15T00:00:00Z,
                Rent,150000,Recurring,2024-02-01,
                Netflix,1599,Recurring,2024-03-01,
                """.trimIndent()
            val file = tempFolder.newFile("import.csv").apply { writeText(csv) }
            val repo = FakeExpenseRepository()
            repo.nextAddExpensesFailure = IllegalStateException("Firestore batch commit failed")
            repo.addExpensesFailureAfterCount = 2
            val viewModel = ImportExportViewModel(repo, context())
            viewModel.onImportFileSelected(Uri.fromFile(file))
            advanceUntilIdle()

            viewModel.onConfirmImport()
            advanceUntilIdle()

            val state = viewModel.importState.value
            assertTrue("expected PartialFailure but was $state", state is ImportUiState.PartialFailure)
            val partial = state as ImportUiState.PartialFailure
            assertEquals(2, partial.succeededCount)
            assertEquals("Firestore batch commit failed", partial.message)

            // The reported succeededCount must match what's actually durably stored, not just a
            // number surfaced in UI state.
            val stored = repo.getAllExpenses().getOrThrow()
            assertEquals(2, stored.size)
            assertEquals(setOf("Coffee", "Rent"), stored.map { it.name }.toSet())
        }

    @Test
    fun onConfirmImport_fullFailure_zeroRowsCommitted_producesErrorNotPartialFailure() =
        runTest(testDispatcher) {
            val csv =
                """
                name,amountCents,category,date,note
                Coffee,500,Shopping,2024-01-15T00:00:00Z,
                Rent,150000,Recurring,2024-02-01,
                """.trimIndent()
            val file = tempFolder.newFile("import.csv").apply { writeText(csv) }
            val repo = FakeExpenseRepository()
            repo.nextAddExpensesFailure = IllegalStateException("Not signed in")
            repo.addExpensesFailureAfterCount = 0
            val viewModel = ImportExportViewModel(repo, context())
            viewModel.onImportFileSelected(Uri.fromFile(file))
            advanceUntilIdle()

            viewModel.onConfirmImport()
            advanceUntilIdle()

            val state = viewModel.importState.value
            assertTrue("expected Error, not PartialFailure, but was $state", state is ImportUiState.Error)
            assertEquals("Not signed in", (state as ImportUiState.Error).message)
            assertTrue(repo.getAllExpenses().getOrThrow().isEmpty())
        }

    @Test
    fun onDismissImport_fromPartialFailureState_resetsStateToIdle() =
        runTest(testDispatcher) {
            val csv = "name,amountCents,category,date,note\nCoffee,500,Shopping,2024-01-15T00:00:00Z,\n"
            val file = tempFolder.newFile("import.csv").apply { writeText(csv) }
            val repo = FakeExpenseRepository()
            repo.nextAddExpensesFailure = IllegalStateException("boom")
            repo.addExpensesFailureAfterCount = 0
            val viewModel = ImportExportViewModel(repo, context())
            viewModel.onImportFileSelected(Uri.fromFile(file))
            advanceUntilIdle()
            viewModel.onConfirmImport()
            advanceUntilIdle()
            assertTrue(viewModel.importState.value is ImportUiState.Error)

            viewModel.onDismissImport()

            assertEquals(ImportUiState.Idle, viewModel.importState.value)
        }

    @Test
    fun exportThenImport_multiLineNoteAndBom_roundTripsThroughFullFileReadParsePreviewPath() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(
                        expense(
                            id = "e1",
                            name = "Team offsite",
                            note = "Paid for everyone.\nWill be reimbursed next month.",
                        ),
                    ),
                )
            val viewModel = ImportExportViewModel(repo, context())
            val file = File(tempFolder.root, "roundtrip.csv")

            viewModel.onExportTargetSelected(Uri.fromFile(file))
            advanceUntilIdle()
            assertEquals(1, (viewModel.exportState.value as ExportUiState.Done).exportedCount)

            // Prepend a BOM to the exported file, as a re-saved "CSV UTF-8" export might have.
            val bomPrefixedBytes = "".toByteArray(Charsets.UTF_8) + file.readBytes()
            file.writeBytes(bomPrefixedBytes)

            viewModel.onImportFileSelected(Uri.fromFile(file))
            advanceUntilIdle()

            val preview = viewModel.importState.value as ImportUiState.Preview
            assertTrue(preview.failures.isEmpty())
            assertEquals(1, preview.validExpenses.size)
            assertEquals("Team offsite", preview.validExpenses.single().name)
            assertEquals(
                "Paid for everyone.\nWill be reimbursed next month.",
                preview.validExpenses.single().note,
            )
        }

    @Test
    fun onDismissImport_resetsStateToIdle() =
        runTest(testDispatcher) {
            val file = tempFolder.newFile("empty.csv")
            val repo = FakeExpenseRepository()
            val viewModel = ImportExportViewModel(repo, context())
            viewModel.onImportFileSelected(Uri.fromFile(file))
            advanceUntilIdle()
            assertTrue(viewModel.importState.value is ImportUiState.Error)

            viewModel.onDismissImport()

            assertEquals(ImportUiState.Idle, viewModel.importState.value)
        }

    // ---- export ----

    @Test
    fun onExportTargetSelected_writesAllExpenses_movesToDoneStateWithCount() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(
                        expense(id = "e1", name = "Coffee"),
                        expense(id = "e2", name = "Rent", amountCents = 150_000L, category = ExpenseCategory.RECURRING),
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
    fun exportThenImport_roundTrip_reimportedExpensesMatchOriginalCount() =
        runTest(testDispatcher) {
            val repo =
                FakeExpenseRepository(
                    listOf(
                        expense(id = "e1", name = "Coffee"),
                        expense(id = "e2", name = "Rent", amountCents = 150_000L, category = ExpenseCategory.RECURRING),
                    ),
                )
            val viewModel = ImportExportViewModel(repo, context())
            val file = File(tempFolder.root, "roundtrip.csv")

            viewModel.onExportTargetSelected(Uri.fromFile(file))
            advanceUntilIdle()
            assertEquals(2, (viewModel.exportState.value as ExportUiState.Done).exportedCount)

            viewModel.onImportFileSelected(Uri.fromFile(file))
            advanceUntilIdle()
            val preview = viewModel.importState.value as ImportUiState.Preview
            assertEquals(2, preview.validExpenses.size)
            assertTrue(preview.failures.isEmpty())

            viewModel.onConfirmImport()
            advanceUntilIdle()
            assertEquals(2, (viewModel.importState.value as ImportUiState.Done).importedCount)

            // No dedupe: re-importing the exported 2 rows on top of the original 2 doubles the count.
            assertEquals(4, repo.getAllExpenses().getOrThrow().size)
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
    fun onImportFileSelected_cancellationDuringFileRead_doesNotSurfaceAsErrorState() =
        runTest(testDispatcher) {
            Robolectric.setupContentProvider(CancellingContentProvider::class.java, CancellingContentProvider.AUTHORITY)
            val repo = FakeExpenseRepository()
            val viewModel = ImportExportViewModel(repo, context())

            viewModel.onImportFileSelected(Uri.parse("content://${CancellingContentProvider.AUTHORITY}/file.csv"))
            advanceUntilIdle()

            // Before the fix, a CancellationException thrown mid-read was caught by the generic
            // `catch (e: Exception)` branch and turned into ImportUiState.Error. Now it must
            // propagate out and cancel the coroutine instead, leaving the state exactly where it
            // was set immediately before entering the try block: Loading.
            assertEquals(ImportUiState.Loading, viewModel.importState.value)
        }

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
    fun onImportFileSelected_genericExceptionDuringFileRead_stillSurfacesAsErrorState_regressionCheck() =
        runTest(testDispatcher) {
            // Sanity check alongside the cancellation tests above: a *non*-cancellation exception
            // during the same read path must still be caught and surfaced as Error, proving the new
            // `catch (e: CancellationException) { throw e }` branch didn't accidentally swallow or
            // reclassify ordinary failures too.
            Robolectric.setupContentProvider(
                GenericFailureContentProvider::class.java,
                GenericFailureContentProvider.AUTHORITY,
            )
            val repo = FakeExpenseRepository()
            val viewModel = ImportExportViewModel(repo, context())

            viewModel.onImportFileSelected(Uri.parse("content://${GenericFailureContentProvider.AUTHORITY}/file.csv"))
            advanceUntilIdle()

            assertTrue(viewModel.importState.value is ImportUiState.Error)
        }
}
