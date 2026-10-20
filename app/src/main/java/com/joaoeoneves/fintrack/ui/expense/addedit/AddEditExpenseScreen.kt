package com.joaoeoneves.fintrack.ui.expense.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.ReadOnlyDateField
import com.joaoeoneves.fintrack.ui.common.displayName
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExpenseScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddEditExpenseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readyForm = (uiState as? AddEditUiState.Ready)?.form

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collectLatest { onSaved() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AddEditExpenseTopBar(
                isEditMode = viewModel.isEditRoute,
                isSaveEnabled = readyForm != null && readyForm.isValid && !readyForm.isSaving,
                onCancel = onCancel,
                onSave = viewModel::onSave,
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is AddEditUiState.Loading -> {
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

            is AddEditUiState.Ready -> {
                ExpenseForm(
                    form = state.form,
                    actions = viewModel.formActions(),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }

            is AddEditUiState.Error -> {
                ErrorState(
                    message = state.message,
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
private fun AddEditExpenseTopBar(
    isEditMode: Boolean,
    isSaveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = {
            val titleRes = if (isEditMode) R.string.expense_edit_title else R.string.action_add_expense
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

/** Bundles [ExpenseForm]'s field-change callbacks to keep its own parameter list short. */
private data class ExpenseFormActions(
    val onNameChanged: (String) -> Unit,
    val onAmountChanged: (String) -> Unit,
    val onCategorySelected: (ExpenseCategory) -> Unit,
    val onDateSelected: (Instant) -> Unit,
    val onNoteChanged: (String) -> Unit,
    val onRefundChanged: (Boolean) -> Unit,
)

private fun AddEditExpenseViewModel.formActions(): ExpenseFormActions =
    ExpenseFormActions(
        onNameChanged = ::onNameChanged,
        onAmountChanged = ::onAmountChanged,
        onCategorySelected = ::onCategorySelected,
        onDateSelected = ::onDateSelected,
        onNoteChanged = ::onNoteChanged,
        onRefundChanged = ::onRefundChanged,
    )

@Composable
private fun ExpenseForm(
    form: ExpenseFormState,
    actions: ExpenseFormActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ExpenseNameAndAmountFields(form = form, actions = actions)

        RefundToggleRow(
            isRefund = form.isRefund,
            onRefundChanged = actions.onRefundChanged,
            modifier = Modifier.fillMaxWidth(),
        )

        ExpenseCategoryField(
            category = form.category,
            onCategorySelected = actions.onCategorySelected,
        )

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
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ExpenseNameAndAmountFields(
    form: ExpenseFormState,
    actions: ExpenseFormActions,
) {
    OutlinedTextField(
        value = form.name,
        onValueChange = actions.onNameChanged,
        label = { Text(stringResource(R.string.expense_field_name)) },
        isError = form.nameError != null,
        supportingText = {
            FieldSupportingText(error = form.nameError, length = form.name.codePointLength(), max = MAX_NAME_LENGTH)
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = form.amountText,
        onValueChange = actions.onAmountChanged,
        label = { Text(stringResource(R.string.field_amount)) },
        isError = form.amountError != null,
        supportingText = { form.amountError?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A whole-row toggle marking the expense as a refund; the row itself carries the switch semantics. */
@Composable
private fun RefundToggleRow(
    isRefund: Boolean,
    onRefundChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.toggleable(
                value = isRefund,
                role = Role.Switch,
                onValueChange = onRefundChanged,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.expense_field_refund),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.expense_field_refund_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = isRefund,
            onCheckedChange = null,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseCategoryField(
    category: ExpenseCategory,
    onCategorySelected: (ExpenseCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = categoryMenuExpanded,
        onExpandedChange = { categoryMenuExpanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            readOnly = true,
            value = category.displayName,
            onValueChange = {},
            label = { Text(stringResource(R.string.expense_field_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = categoryMenuExpanded,
            onDismissRequest = { categoryMenuExpanded = false },
        ) {
            ExpenseCategory.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.displayName) },
                    onClick = {
                        onCategorySelected(entry)
                        categoryMenuExpanded = false
                    },
                )
            }
        }
    }
}
