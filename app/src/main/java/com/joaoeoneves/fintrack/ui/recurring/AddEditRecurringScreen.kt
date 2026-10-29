package com.joaoeoneves.fintrack.ui.recurring

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.optionsIncluding
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.FormSaveError
import com.joaoeoneves.fintrack.ui.common.FormScaffold
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import com.joaoeoneves.fintrack.ui.designsystem.rememberIsDirty
import com.joaoeoneves.fintrack.ui.theme.Dimens
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AddEditRecurringScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEditRecurringViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categoryOptions by viewModel.categoryOptions.collectAsStateWithLifecycle()
    val ready = uiState as? AddEditRecurringUiState.Ready

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collectLatest { onSaved() }
    }

    FormScaffold(
        title = stringResource(if (viewModel.isEditRoute) R.string.recurring_edit_title else R.string.recurring_add),
        onCancel = onCancel,
        saveEnabled = ready != null && !ready.isSaving,
        onSave = viewModel::onSave,
        showSave = ready != null,
        dirty = rememberIsDirty(ready?.form),
        modifier = modifier,
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (val state = uiState) {
            AddEditRecurringUiState.Loading -> Unit

            is AddEditRecurringUiState.Ready ->
                RecurringFormContent(
                    state = state,
                    categoryOptions =
                        categoryOptions.optionsIncluding(state.form.category).forNewItem(!state.isEditMode),
                    onFormChanged = viewModel::onFormChanged,
                    onDelete = viewModel::onDelete,
                    modifier = contentModifier,
                )

            is AddEditRecurringUiState.Error ->
                ErrorState(message = state.message.asString(), onRetry = viewModel::onRetry, modifier = contentModifier)
        }
    }
}

@Composable
private fun RecurringFormContent(
    state: AddEditRecurringUiState.Ready,
    categoryOptions: List<Category>,
    onFormChanged: (RecurringForm) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val errors = state.errors
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space16),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
    ) {
        RecurringTypeAndNameFields(form, errors, onFormChanged, autoFocusAmount = !state.isEditMode)
        RecurringCategoryField(form, categoryOptions, errors, onFormChanged, showSavingsHint = !state.isEditMode)
        RecurringScheduleFields(form, errors, onFormChanged)
        RecurringDateFields(form, errors, onFormChanged)
        MinimalTextField(
            value = form.note,
            onValueChange = { onFormChanged(form.copy(note = it)) },
            label = stringResource(R.string.field_note_optional),
            supportingText = {
                FieldSupportingText(
                    error = errors?.note?.asString(),
                    length = form.note.codePointLength(),
                    max = MAX_NOTE_LENGTH,
                )
            },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        RecurringToggles(form, state.isEditMode, onFormChanged)
        state.saveError?.let { error ->
            FormSaveError(error.asString())
        }
        if (state.isEditMode) {
            DestructiveTextButton(
                text = stringResource(R.string.cd_delete),
                onClick = { confirmDelete = true },
                enabled = !state.isSaving,
            )
        }
    }

    if (confirmDelete) {
        RecurringDeleteDialog(
            name = form.name,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** New items cannot go to a SAVING-kind category (those belong to savings plans); existing items keep theirs. */
private fun List<Category>.forNewItem(isNew: Boolean) = if (isNew) filter { it.kind != CategoryKind.SAVING } else this

/** The auto-post switch and, when editing, the pause switch. */
@Composable
private fun RecurringToggles(
    form: RecurringForm,
    isEditMode: Boolean,
    onFormChanged: (RecurringForm) -> Unit,
) {
    SettingsSwitchRow(
        title = stringResource(R.string.recurring_auto_post),
        checked = form.autoPost,
        onCheckedChange = { onFormChanged(form.copy(autoPost = it)) },
        supportingText = stringResource(R.string.recurring_auto_post_hint),
    )
    if (isEditMode) {
        SettingsSwitchRow(
            title = stringResource(R.string.recurring_paused),
            checked = form.paused,
            onCheckedChange = { onFormChanged(form.copy(paused = it)) },
            supportingText = stringResource(R.string.recurring_pause_hint),
        )
    }
}
