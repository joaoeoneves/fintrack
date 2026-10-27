package com.joaoeoneves.fintrack.ui.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.backup.BackupCodec
import com.joaoeoneves.fintrack.data.backup.BackupDecodeResult
import com.joaoeoneves.fintrack.data.backup.BackupDocument
import com.joaoeoneves.fintrack.data.backup.BackupRepository
import com.joaoeoneves.fintrack.data.backup.counts
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.di.IoDispatcher
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "BackupViewModel"

/** Largest backup file that is read (guards against opening an unrelated huge file). */
const val MAX_BACKUP_BYTES = 20 * 1024 * 1024

private const val READ_BUFFER_BYTES = 8 * 1024

private class BackupTooLargeException : IOException("Backup file exceeds $MAX_BACKUP_BYTES bytes")

/** Drives the JSON backup export and the (guest-only) restore, including the SAF file I/O. */
@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val clock: Clock,
    ) : ViewModel() {
        private val _exportState = MutableStateFlow<BackupExportState>(BackupExportState.Idle)
        val exportState: StateFlow<BackupExportState> = _exportState.asStateFlow()

        private val _importState = MutableStateFlow<BackupImportState>(BackupImportState.Idle)
        val importState: StateFlow<BackupImportState> = _importState.asStateFlow()

        private var pending: BackupDocument? = null

        /** Default file name offered by the save dialog, e.g. `fintrack-backup-2026-10-27.json`. */
        fun suggestedFileName(): String = "fintrack-backup-${LocalDate.now(clock)}.json"

        fun onExportTargetSelected(uri: Uri) {
            viewModelScope.launch {
                _exportState.value = BackupExportState.Exporting
                _exportState.value =
                    backupRepository
                        .buildBackup(clock.instant())
                        .fold(
                            onSuccess = { document -> writeBackup(uri, document) },
                            onFailure = {
                                Log.w(TAG, "Failed to build backup", it)
                                BackupExportState.Error(UiText.StringResource(R.string.backup_error_export_failed))
                            },
                        )
            }
        }

        private suspend fun writeBackup(
            uri: Uri,
            document: BackupDocument,
        ): BackupExportState {
            val wrote =
                runCatchingCancellable {
                    withContext(ioDispatcher) {
                        val bytes = BackupCodec.encode(document).toByteArray(Charsets.UTF_8)
                        val resolver = context.contentResolver
                        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("No stream")
                        stream.use { it.write(bytes) }
                    }
                }.onFailure { Log.w(TAG, "Failed to write backup file", it) }
            return if (wrote.isSuccess) {
                BackupExportState.Done(document.counts())
            } else {
                BackupExportState.Error(UiText.StringResource(R.string.backup_error_write))
            }
        }

        fun onDismissExport() {
            _exportState.value = BackupExportState.Idle
        }

        fun onImportFileSelected(uri: Uri) {
            viewModelScope.launch {
                pending = null
                _importState.value = BackupImportState.Reading
                _importState.value =
                    runCatchingCancellable { stageDecoded(readText(uri)) }
                        .getOrElse { readFailure(it) }
            }
        }

        private suspend fun stageDecoded(text: String): BackupImportState =
            when (val result = withContext(ioDispatcher) { BackupCodec.decode(text) }) {
                is BackupDecodeResult.Success -> {
                    pending = result.document
                    BackupImportState.Confirm(
                        counts = result.document.counts(),
                        skippedCount = result.skippedCount,
                        localHasData = backupRepository.localHasData(),
                    )
                }

                is BackupDecodeResult.UnsupportedVersion ->
                    BackupImportState.Error(UiText.StringResource(R.string.backup_error_unsupported_version))

                is BackupDecodeResult.Invalid ->
                    BackupImportState.Error(UiText.StringResource(R.string.backup_error_invalid))
            }

        private fun readFailure(error: Throwable): BackupImportState {
            Log.w(TAG, "Failed to read backup file", error)
            val message =
                if (error is BackupTooLargeException) R.string.backup_error_too_large else R.string.backup_error_read
            return BackupImportState.Error(UiText.StringResource(message))
        }

        private suspend fun readText(uri: Uri): String =
            withContext(ioDispatcher) {
                val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("No stream")
                stream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(READ_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (out.size() + read > MAX_BACKUP_BYTES) throw BackupTooLargeException()
                        out.write(buffer, 0, read)
                    }
                    out.toString(Charsets.UTF_8.name())
                }
            }

        fun onConfirmImport() {
            val document = pending
            if (_importState.value !is BackupImportState.Confirm || document == null) return
            viewModelScope.launch {
                _importState.value = BackupImportState.Restoring
                _importState.value =
                    backupRepository.restoreAsGuest(document).fold(
                        onSuccess = {
                            pending = null
                            BackupImportState.Done(document.counts())
                        },
                        onFailure = {
                            BackupImportState.Error(UiText.StringResource(R.string.backup_error_restore_failed))
                        },
                    )
            }
        }

        fun onDismissImport() {
            pending = null
            _importState.value = BackupImportState.Idle
        }
    }
