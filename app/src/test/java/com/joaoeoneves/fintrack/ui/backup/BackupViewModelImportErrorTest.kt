package com.joaoeoneves.fintrack.ui.backup

import android.net.Uri
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.backup.backupDoc
import org.junit.Test
import java.io.File

class BackupViewModelImportErrorTest : BackupViewModelTestBase() {
    private fun error(vm: BackupViewModel) = (vm.importState.value as BackupImportState.Error).message

    @Test
    fun select_unreadableFile_emitsReadError() =
        runStoreTest {
            val vm = viewModel()

            vm.onImportFileSelected(Uri.fromFile(File(tmp.root, "missing.json")))
            settle()

            assertError(R.string.backup_error_read, error(vm))
        }

    @Test
    fun select_fileOverLimit_emitsTooLarge() =
        runStoreTest {
            val file = tmp.newFile("big.json")
            file.outputStream().use { it.write(ByteArray(MAX_BACKUP_BYTES + 1) { 'a'.code.toByte() }) }
            val vm = viewModel()

            vm.onImportFileSelected(Uri.fromFile(file))
            settle()

            assertError(R.string.backup_error_too_large, error(vm))
        }

    @Test
    fun select_fileExactlyAtLimit_isReadNotRejectedAsTooLarge() =
        runStoreTest {
            val file = tmp.newFile("edge.json")
            file.outputStream().use { it.write(ByteArray(MAX_BACKUP_BYTES) { 'a'.code.toByte() }) }
            val vm = viewModel()

            vm.onImportFileSelected(Uri.fromFile(file))
            settle()

            assertError(R.string.backup_error_invalid, error(vm))
        }

    @Test
    fun select_notABackup_emitsInvalid() =
        runStoreTest {
            val vm = viewModel()

            vm.onImportFileSelected(fileWith("{\"hello\":1}"))
            settle()

            assertError(R.string.backup_error_invalid, error(vm))
        }

    @Test
    fun select_newerVersion_emitsUnsupported() =
        runStoreTest {
            val vm = viewModel()

            vm.onImportFileSelected(
                fileWith("""{"format":"fintrack-backup","schemaVersion":10,"exportedAt":"2026-01-01T00:00:00Z"}"""),
            )
            settle()

            assertError(R.string.backup_error_unsupported_version, error(vm))
        }

    @Test
    fun select_localHasDataFails_emitsReadError_notStuckReading() =
        runStoreTest {
            dataFile.mkdirs()
            val vm = viewModel()

            vm.onImportFileSelected(fileWith(encode(backupDoc())))
            settle()

            assertError(R.string.backup_error_read, error(vm))
        }
}
