package com.joaoeoneves.fintrack.ui.income.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.rememberLocaleAwareDateFormatter
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditIncomeScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEditIncomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collectLatest { onSaved() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AddEditIncomeTopBar(
                isEditMode = (uiState as? AddEditIncomeUiState.Ready)?.form?.isEditMode ?: false,
                isSaveEnabled = (uiState as? AddEditIncomeUiState.Ready)?.form?.isValid == true,
                onCancel = onCancel,
                onSave = viewModel::onSave,
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is AddEditIncomeUiState.Loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is AddEditIncomeUiState.Ready -> {
                IncomeForm(
                    form = state.form,
                    onSourceChanged = viewModel::onSourceChanged,
                    onAmountChanged = viewModel::onAmountChanged,
                    onDateSelected = viewModel::onDateSelected,
                    onNoteChanged = viewModel::onNoteChanged,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditIncomeTopBar(
    isEditMode: Boolean,
    isSaveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = {
            val titleRes = if (isEditMode) R.string.income_edit_title else R.string.action_add_income
            Text(stringResource(titleRes))
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                val cancelCd = stringResource(R.string.cd_cancel)
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = cancelCd)
            }
        },
        actions = {
            TextButton(onClick = onSave, enabled = isSaveEnabled) {
                Text(stringResource(R.string.action_save))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomeForm(
    form: IncomeFormState,
    onSourceChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onDateSelected: (Instant) -> Unit,
    onNoteChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val displayDateFormatter = rememberLocaleAwareDateFormatter().withZone(ZoneOffset.UTC)

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = form.source,
            onValueChange = onSourceChanged,
            label = { Text(stringResource(R.string.income_field_source)) },
            isError = form.sourceError != null,
            supportingText = { form.sourceError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.amountText,
            onValueChange = onAmountChanged,
            label = { Text(stringResource(R.string.field_amount)) },
            isError = form.amountError != null,
            supportingText = { form.amountError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            readOnly = true,
            value = displayDateFormatter.format(form.date),
            onValueChange = {},
            label = { Text(stringResource(R.string.field_date)) },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    val changeDateCd = stringResource(R.string.cd_change_date)
                    Icon(Icons.Default.DateRange, contentDescription = changeDateCd)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.note,
            onValueChange = onNoteChanged,
            label = { Text(stringResource(R.string.field_note_optional)) },
            modifier = Modifier.fillMaxWidth(),
        )

        form.saveError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (showDatePicker) {
        val datePickerState =
            androidx.compose.material3.rememberDatePickerState(
                initialSelectedDateMillis = form.date.toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis))
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
