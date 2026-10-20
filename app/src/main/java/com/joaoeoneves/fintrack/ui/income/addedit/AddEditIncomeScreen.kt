package com.joaoeoneves.fintrack.ui.income.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.ReadOnlyDateField
import com.joaoeoneves.fintrack.ui.common.asString
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditIncomeScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEditIncomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readyForm = (uiState as? AddEditIncomeUiState.Ready)?.form

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collectLatest { onSaved() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AddEditIncomeTopBar(
                isEditMode = viewModel.isEditRoute,
                isSaveEnabled = readyForm != null && readyForm.isValid && !readyForm.isSaving,
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
                    actions =
                        IncomeFormActions(
                            onSourceChanged = viewModel::onSourceChanged,
                            onAmountChanged = viewModel::onAmountChanged,
                            onDateSelected = viewModel::onDateSelected,
                            onNoteChanged = viewModel::onNoteChanged,
                        ),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }

            is AddEditIncomeUiState.Error -> {
                ErrorState(
                    message = state.message.asString(),
                    onRetry = viewModel::onRetry,
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

/** Bundles [IncomeForm]'s field-change callbacks to keep its own parameter list short. */
private data class IncomeFormActions(
    val onSourceChanged: (String) -> Unit,
    val onAmountChanged: (String) -> Unit,
    val onDateSelected: (Instant) -> Unit,
    val onNoteChanged: (String) -> Unit,
)

@Composable
private fun IncomeForm(
    form: IncomeFormState,
    actions: IncomeFormActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IncomeSourceAndAmountFields(form = form, actions = actions)

        ReadOnlyDateField(
            date = form.date,
            onDateSelected = actions.onDateSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.note,
            onValueChange = actions.onNoteChanged,
            label = { Text(stringResource(R.string.field_note_optional)) },
            supportingText = {
                FieldSupportingText(error = null, length = form.note.codePointLength(), max = MAX_NOTE_LENGTH)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        form.saveError?.let { error ->
            Text(
                text = error.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun IncomeSourceAndAmountFields(
    form: IncomeFormState,
    actions: IncomeFormActions,
) {
    OutlinedTextField(
        value = form.source,
        onValueChange = actions.onSourceChanged,
        label = { Text(stringResource(R.string.income_field_source)) },
        isError = form.sourceError != null,
        supportingText = {
            FieldSupportingText(
                error = form.sourceError?.asString(),
                length = form.source.codePointLength(),
                max = MAX_NAME_LENGTH,
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = form.amountText,
        onValueChange = actions.onAmountChanged,
        label = { Text(stringResource(R.string.field_amount)) },
        isError = form.amountError != null,
        supportingText = { form.amountError?.let { Text(it.asString()) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
