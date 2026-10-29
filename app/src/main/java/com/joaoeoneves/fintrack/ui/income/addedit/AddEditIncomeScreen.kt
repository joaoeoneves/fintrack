package com.joaoeoneves.fintrack.ui.income.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.ui.common.DeletedEffect
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.FormDeleteButton
import com.joaoeoneves.fintrack.ui.common.FormSaveError
import com.joaoeoneves.fintrack.ui.common.FormScaffold
import com.joaoeoneves.fintrack.ui.common.ReadOnlyDateField
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.rememberCurrencyAffix
import com.joaoeoneves.fintrack.ui.common.symbol
import com.joaoeoneves.fintrack.ui.common.symbolLeads
import com.joaoeoneves.fintrack.ui.designsystem.BorderlessAmountField
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.rememberIsDirty
import com.joaoeoneves.fintrack.ui.theme.Dimens
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant

@Composable
fun AddEditIncomeScreen(
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEditIncomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readyForm = (uiState as? AddEditIncomeUiState.Ready)?.form

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collectLatest { onSaved() }
    }
    DeletedEffect(viewModel.deletedEvent, onDeleted)

    FormScaffold(
        title = stringResource(if (viewModel.isEditRoute) R.string.income_edit_title else R.string.action_add_income),
        onCancel = onCancel,
        saveEnabled = readyForm != null && !readyForm.isBusy,
        onSave = viewModel::onSave,
        showSave = readyForm != null,
        dirty = rememberIsDirty(readyForm?.copy(sourceError = null, amountError = null, saveError = null)),
        modifier = modifier,
    ) { innerPadding ->
        when (val state = uiState) {
            is AddEditIncomeUiState.Loading -> Unit

            is AddEditIncomeUiState.Ready -> {
                IncomeForm(
                    form = state.form,
                    isEditMode = viewModel.isEditRoute,
                    actions =
                        IncomeFormActions(
                            onSourceChanged = viewModel::onSourceChanged,
                            onAmountChanged = viewModel::onAmountChanged,
                            onDateSelected = viewModel::onDateSelected,
                            onNoteChanged = viewModel::onNoteChanged,
                            onDelete = viewModel::onDelete,
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

/** Bundles [IncomeForm]'s field-change callbacks to keep its own parameter list short. */
private data class IncomeFormActions(
    val onSourceChanged: (String) -> Unit,
    val onAmountChanged: (Long) -> Unit,
    val onDateSelected: (Instant) -> Unit,
    val onNoteChanged: (String) -> Unit,
    val onDelete: () -> Unit,
)

@Composable
private fun IncomeForm(
    form: IncomeFormState,
    isEditMode: Boolean,
    actions: IncomeFormActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space16),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
    ) {
        val affix = rememberCurrencyAffix()
        BorderlessAmountField(
            cents = form.amountCents,
            onCentsChange = actions.onAmountChanged,
            currencySymbol = affix.symbol,
            symbolLeads = affix.leads,
            label = stringResource(R.string.field_amount),
            isError = form.amountError != null,
            autoFocus = !isEditMode,
            modifier = Modifier.padding(vertical = Dimens.Space16),
        )
        form.amountError?.let { FormSaveError(it.asString(), Modifier.fillMaxWidth()) }

        MinimalTextField(
            value = form.source,
            onValueChange = actions.onSourceChanged,
            label = stringResource(R.string.income_field_source),
            isError = form.sourceError != null,
            supportingText = {
                FieldSupportingText(
                    error = form.sourceError?.asString(),
                    length = form.source.codePointLength(),
                    max = MAX_NAME_LENGTH,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        ReadOnlyDateField(
            date = form.date,
            onDateSelected = actions.onDateSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        MinimalTextField(
            value = form.note,
            onValueChange = actions.onNoteChanged,
            label = stringResource(R.string.field_note_optional),
            supportingText = {
                FieldSupportingText(error = null, length = form.note.codePointLength(), max = MAX_NOTE_LENGTH)
            },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )

        form.saveError?.let { FormSaveError(it.asString()) }
        if (form.canDelete) FormDeleteButton(onClick = actions.onDelete, enabled = !form.isBusy)
    }
}
