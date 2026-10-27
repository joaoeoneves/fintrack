package com.joaoeoneves.fintrack.ui.backup

import android.net.Uri
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.backup.BackupCodec
import com.joaoeoneves.fintrack.data.backup.BackupCounts
import com.joaoeoneves.fintrack.data.backup.BackupDecodeResult
import com.joaoeoneves.fintrack.data.backup.success
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

class BackupViewModelExportTest : BackupViewModelTestBase() {
    private val expense = testExpense("e1", 500, Instant.parse("2026-03-10T12:00:00Z"))

    @Test
    fun suggestedFileName_usesClockDate() {
        assertEquals("fintrack-backup-2026-10-27.json", viewModel().suggestedFileName())
    }

    @Test
    fun suggestedFileName_followsClockZone() {
        clock.now = Instant.parse("2026-12-31T23:30:00Z")
        val vm = viewModel()

        assertEquals("fintrack-backup-2026-12-31.json", vm.suggestedFileName())
        assertEquals(ZoneOffset.UTC, clock.zone)
    }

    @Test
    fun export_success_writesDecodableFileAndEmitsDone() =
        runStoreTest {
            val (file, uri) = newFileUri()
            val vm = viewModel(expenses = listOf(expense))

            vm.onExportTargetSelected(uri)
            settle()

            assertEquals(BackupExportState.Done(BackupCounts(1, 0, 0, 0, 0)), vm.exportState.value)
            val doc = BackupCodec.decode(file.readText()).success().document
            assertEquals(listOf("e1"), doc.expenses.map { it.id })
            assertEquals("2026-10-27T10:00:00Z", doc.exportedAt)
        }

    @Test
    fun export_overwritesExistingLongerFile() =
        runStoreTest {
            val (file, uri) = newFileUri()
            file.writeText("x".repeat(100_000))

            val vm = viewModel()
            vm.onExportTargetSelected(uri)
            settle()

            assertTrue(BackupCodec.decode(file.readText()) is BackupDecodeResult.Success)
        }

    @Test
    fun export_buildFailure_emitsExportFailedError() =
        runStoreTest {
            val (file, uri) = newFileUri()
            val vm = viewModel(repository = failingRepository())

            vm.onExportTargetSelected(uri)
            settle()

            assertEquals(
                BackupExportState.Error(UiText.StringResource(R.string.backup_error_export_failed)),
                vm.exportState.value,
            )
            assertEquals("", file.readText())
        }

    @Test
    fun export_unwritableTarget_emitsWriteError() =
        runStoreTest {
            val vm = viewModel()

            vm.onExportTargetSelected(Uri.fromFile(File(tmp.root, "no-such-dir/out.json")))
            settle()

            val state = vm.exportState.value as BackupExportState.Error
            assertError(R.string.backup_error_write, state.message)
        }

    @Test
    fun dismissExport_returnsToIdle() =
        runStoreTest {
            val (_, uri) = newFileUri()
            val vm = viewModel()
            vm.onExportTargetSelected(uri)
            settle()

            vm.onDismissExport()

            assertEquals(BackupExportState.Idle, vm.exportState.value)
        }
}
