package com.joaoeoneves.fintrack.ui.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.data.csv.CsvImportOutcome
import com.joaoeoneves.fintrack.data.csv.ExpenseCsvParser
import com.joaoeoneves.fintrack.data.csv.ExpenseCsvWriter
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportExportViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        @ApplicationContext private val context: Context,
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
                    } catch (e: Exception) {
                        null
                    }
                if (text == null) {
                    _importState.value = ImportUiState.Error("Could not read the selected file")
                    return@launch
                }
                _importState.value =
                    when (val outcome = ExpenseCsvParser.parse(text)) {
                        is CsvImportOutcome.Parsed ->
                            ImportUiState.Preview(outcome.result.validExpenses, outcome.result.failures)
                        is CsvImportOutcome.Error -> ImportUiState.Error(outcome.message)
                    }
            }
        }

        fun onConfirmImport() {
            val preview = _importState.value as? ImportUiState.Preview ?: return
            viewModelScope.launch {
                _importState.value = ImportUiState.Importing
                expenseRepository
                    .addExpenses(preview.validExpenses)
                    .onSuccess { count -> _importState.value = ImportUiState.Done(count) }
                    .onFailure { e -> _importState.value = ImportUiState.Error(e.message ?: "Import failed") }
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
                            } catch (e: Exception) {
                                false
                            }
                        _exportState.value =
                            if (wrote) {
                                ExportUiState.Done(expenses.size)
                            } else {
                                ExportUiState.Error("Could not write the export file")
                            }
                    }.onFailure { e -> _exportState.value = ExportUiState.Error(e.message ?: "Export failed") }
            }
        }

        fun onDismissExport() {
            _exportState.value = ExportUiState.Idle
        }
    }
