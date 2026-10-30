package com.joaoeoneves.fintrack.ui.importexport

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.csv.ExpenseCsvWriter
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.ui.common.UiText
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
        private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
        val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

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
                                // Broad catch: content-provider I/O can throw undocumented exception types.
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
                        Log.w(TAG, "Export failed", e)
                        _exportState.value = ExportUiState.Error(UiText.StringResource(R.string.error_export_failed))
                    }
            }
        }

        fun onDismissExport() {
            _exportState.value = ExportUiState.Idle
        }
    }
