package com.joaoeoneves.fintrack.ui.importexport

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.csv.CsvImportOutcome
import com.joaoeoneves.fintrack.data.csv.ExpenseCsvParser
import com.joaoeoneves.fintrack.data.csv.ExpenseCsvWriter
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.messageOr
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ImportExportViewModel"

@HiltViewModel
class ImportExportViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
        val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

        private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
        val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

        fun onImportFileSelected(uri: Uri) {
            viewModelScope.launch {
                _importState.value = ImportUiState.Loading
                val text =
                    try {
                        context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Broad catch: content-provider I/O can throw undocumented exception
                        // types depending on the provider implementation; log and fall back to
                        // a generic read-failure message for the user.
                        Log.w(TAG, "Failed to read import file", e)
                        null
                    }
                if (text == null) {
                    _importState.value = ImportUiState.Error(UiText.StringResource(R.string.error_import_read_file))
                    return@launch
                }
                _importState.value =
                    when (val outcome = ExpenseCsvParser.parse(text)) {
                        is CsvImportOutcome.Parsed ->
                            ImportUiState.Preview(outcome.result.validExpenses, outcome.result.failures)
                        is CsvImportOutcome.Error -> ImportUiState.Error(UiText.Dynamic(outcome.message))
                    }
            }
        }

        fun onConfirmImport() {
            val preview = _importState.value as? ImportUiState.Preview ?: return
            viewModelScope.launch {
                _importState.value = ImportUiState.Importing
                val result = expenseRepository.addExpenses(preview.validExpenses)
                _importState.value =
                    when {
                        result.isCompleteSuccess -> ImportUiState.Done(result.succeededCount)
                        result.succeededCount > 0 ->
                            ImportUiState.PartialFailure(
                                result.succeededCount,
                                result.failure.messageOr(R.string.error_import_failed),
                            )
                        else -> ImportUiState.Error(result.failure.messageOr(R.string.error_import_failed))
                    }
            }
        }

        fun onDismissImport() {
            _importState.value = ImportUiState.Idle
        }

        fun onExportTargetSelected(uri: Uri) {
            viewModelScope.launch {
                _exportState.value = ExportUiState.Exporting
                expenseRepository
                    .getAllExpenses()
                    .onSuccess { expenses ->
                        val csv = ExpenseCsvWriter.write(expenses)
                        val wrote =
                            try {
                                val stream = context.contentResolver.openOutputStream(uri)
                                stream?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                                stream != null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // Broad catch: see onImportFileSelected above.
                                Log.w(TAG, "Failed to write export file", e)
                                false
                            }
                        _exportState.value =
                            if (wrote) {
                                ExportUiState.Done(expenses.size)
                            } else {
                                ExportUiState.Error(UiText.StringResource(R.string.error_export_write_file))
                            }
                    }.onFailure { e ->
                        _exportState.value = ExportUiState.Error(e.messageOr(R.string.error_export_failed))
                    }
            }
        }

        fun onDismissExport() {
            _exportState.value = ExportUiState.Idle
        }
    }
