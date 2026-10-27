package com.joaoeoneves.fintrack.ui.importexport

import android.app.Application
import android.net.Uri
import com.joaoeoneves.fintrack.data.CategoryResolvingExpenseRepository
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.FirestoreCategoryRepository
import com.joaoeoneves.fintrack.data.FirestoreExpenseRepository
import com.joaoeoneves.fintrack.data.local.LocalCategoryRepository
import com.joaoeoneves.fintrack.data.local.LocalExpenseRepository
import com.joaoeoneves.fintrack.data.local.LocalStoreTestBase
import com.joaoeoneves.fintrack.data.session.SessionCategoryRepository
import com.joaoeoneves.fintrack.data.session.SessionExpenseRepository
import com.joaoeoneves.fintrack.domain.model.Session
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** CSV import then export as a guest, through the full guest repository stack (local file store). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImportExportGuestRoundTripTest : LocalStoreTestBase() {
    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): ImportExportViewModel {
        val sessions = FakeSessionRepository(initialSession = Session.Guest)
        val store = newStore()
        val ids = SequenceIds("x")
        val categories =
            SessionCategoryRepository(
                sessions,
                Lazy<FirestoreCategoryRepository> { error("no Firestore as guest") },
                LocalCategoryRepository(store, ids),
            )
        val raw =
            SessionExpenseRepository(
                sessions,
                Lazy<FirestoreExpenseRepository> { error("no Firestore as guest") },
                LocalExpenseRepository(store, ids),
            )
        return ImportExportViewModel(
            CategoryResolvingExpenseRepository(raw, categories),
            categories,
            RuntimeEnvironment.getApplication(),
        )
    }

    @Test
    fun importThenExport_roundTripsThroughLocalStore() =
        runStoreTest {
            val csv =
                """
                name,amountCents,category,date,note
                Coffee,500,Shopping,2024-01-15T00:00:00Z,with milk
                Rent,150000,Recurring,2024-02-01T00:00:00Z,
                """.trimIndent()
            val input = tmp.newFile("in.csv").apply { writeText(csv) }
            val output = tmp.newFile("out.csv")
            val vm = viewModel()

            vm.onImportFileSelected(Uri.fromFile(input))
            advanceUntilIdle()
            assertTrue(vm.importState.value is ImportUiState.Preview)
            vm.onConfirmImport()
            advanceUntilIdle()
            assertTrue(vm.importState.value.toString(), vm.importState.value is ImportUiState.Done)

            assertEquals(2, diskDoc().expenses.size)

            vm.onExportTargetSelected(Uri.fromFile(output))
            advanceUntilIdle()
            assertEquals(ExportUiState.Done(2), vm.exportState.value)
            val exported = output.readText()
            assertTrue(exported, exported.contains("Coffee") && exported.contains("with milk"))
            assertTrue(exported, exported.contains("Rent") && exported.contains("150000"))
        }
}
