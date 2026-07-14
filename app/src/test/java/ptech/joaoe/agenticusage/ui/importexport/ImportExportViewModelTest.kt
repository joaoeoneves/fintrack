package ptech.joaoe.agenticusage.ui.importexport

import android.app.Application
import android.net.Uri
import java.io.File
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ptech.joaoe.agenticusage.data.FakeExpenseRepository
import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
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
        note: String? = null
    ) = Expense(id = id, name = name, amountCents = amountCents, category = category, date = date, note = note)

    // ---- import ----

    @Test
    fun onImportFileSelected_validCsv_movesToPreviewState_withParsedExpenses() = runTest(testDispatcher) {
        val csv = """
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
    fun onImportFileSelected_csvWithSomeBadRows_previewIncludesFailures() = runTest(testDispatcher) {
        val csv = """
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
    fun onImportFileSelected_emptyFile_movesToErrorState() = runTest(testDispatcher) {
        val file = tempFolder.newFile("empty.csv")
        val repo = FakeExpenseRepository()
        val viewModel = ImportExportViewModel(repo, context())

        viewModel.onImportFileSelected(Uri.fromFile(file))
        advanceUntilIdle()

        assertTrue(viewModel.importState.value is ImportUiState.Error)
    }

    @Test
    fun onImportFileSelected_missingFile_movesToErrorState_doesNotCrash() = runTest(testDispatcher) {
        val missing = File(tempFolder.root, "does-not-exist.csv")
        val repo = FakeExpenseRepository()
        val viewModel = ImportExportViewModel(repo, context())

        viewModel.onImportFileSelected(Uri.fromFile(missing))
        advanceUntilIdle()

        assertTrue(viewModel.importState.value is ImportUiState.Error)
    }

    @Test
    fun onConfirmImport_afterPreview_movesToDoneState_andRepositoryContainsNewExpenses() = runTest(testDispatcher) {
        val csv = """
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
    fun onConfirmImport_appendsToExistingExpenses_doesNotReplaceThem() = runTest(testDispatcher) {
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

    @Test
    fun onDismissImport_resetsStateToIdle() = runTest(testDispatcher) {
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
    fun onExportTargetSelected_writesAllExpenses_movesToDoneStateWithCount() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository(
            listOf(
                expense(id = "e1", name = "Coffee"),
                expense(id = "e2", name = "Rent", amountCents = 150_000L, category = ExpenseCategory.RECURRING)
            )
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
    fun onExportTargetSelected_emptyRepository_writesHeaderOnly_countZero() = runTest(testDispatcher) {
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
    fun exportThenImport_roundTrip_reimportedExpensesMatchOriginalCount() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository(
            listOf(
                expense(id = "e1", name = "Coffee"),
                expense(id = "e2", name = "Rent", amountCents = 150_000L, category = ExpenseCategory.RECURRING)
            )
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
    fun onDismissExport_resetsStateToIdle() = runTest(testDispatcher) {
        val repo = FakeExpenseRepository()
        val viewModel = ImportExportViewModel(repo, context())
        val target = File(tempFolder.root, "export.csv")
        viewModel.onExportTargetSelected(Uri.fromFile(target))
        advanceUntilIdle()
        assertTrue(viewModel.exportState.value is ExportUiState.Done)

        viewModel.onDismissExport()

        assertEquals(ExportUiState.Idle, viewModel.exportState.value)
    }
}
