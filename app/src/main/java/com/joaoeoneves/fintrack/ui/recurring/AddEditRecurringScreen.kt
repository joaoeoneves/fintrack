package com.joaoeoneves.fintrack.ui.recurring

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.optionsIncluding
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.asString
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

    Scaffold(
        modifier = modifier,
        topBar = {
            AddEditRecurringTopBar(
                isEditMode = viewModel.isEditRoute,
                isSaveEnabled = ready != null && !ready.isSaving,
                onCancel = onCancel,
                onSave = viewModel::onSave,
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (val state = uiState) {
            AddEditRecurringUiState.Loading ->
                Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            is AddEditRecurringUiState.Ready ->
                RecurringFormContent(
                    state = state,
                    categoryOptions = categoryOptions.optionsIncluding(state.form.category),
                    onFormChanged = viewModel::onFormChanged,
                    onDelete = viewModel::onDelete,
                    modifier = contentModifier,
                )

            is AddEditRecurringUiState.Error ->
                ErrorState(message = state.message.asString(), onRetry = viewModel::onRetry, modifier = contentModifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditRecurringTopBar(
    isEditMode: Boolean,
    isSaveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(stringResource(if (isEditMode) R.string.recurring_edit_title else R.string.recurring_add))
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_cancel))
            }
        },
        actions = {
            TextButton(onClick = onSave, enabled = isSaveEnabled) { Text(stringResource(R.string.action_save)) }
        },
    )
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
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RecurringTypeAndNameFields(form, errors, onFormChanged)
        if (form.type == RecurringType.EXPENSE) {
            RecurringCategoryField(form, categoryOptions, errors, onFormChanged)
        }
        RecurringScheduleFields(form, errors, onFormChanged)
        RecurringDateFields(form, errors, onFormChanged)
        OutlinedTextField(
            value = form.note,
            onValueChange = { onFormChanged(form.copy(note = it)) },
            label = { Text(stringResource(R.string.field_note_optional)) },
            supportingText = {
                FieldSupportingText(
                    error = errors?.note?.asString(),
                    length = form.note.codePointLength(),
                    max = MAX_NOTE_LENGTH,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        state.saveError?.let { error ->
            Text(
                text = error.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.isEditMode) {
            TextButton(onClick = { confirmDelete = true }, enabled = !state.isSaving) {
                Text(stringResource(R.string.cd_delete))
            }
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
