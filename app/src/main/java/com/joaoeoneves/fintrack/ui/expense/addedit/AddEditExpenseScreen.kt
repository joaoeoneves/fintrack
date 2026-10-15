package com.joaoeoneves.fintrack.ui.expense.addedit

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
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.ui.common.ErrorState
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

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collectLatest { onSaved() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AddEditExpenseTopBar(
                isEditMode = viewModel.isEditRoute,
                isSaveEnabled = (uiState as? AddEditUiState.Ready)?.form?.isValid == true,
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
                    actions =
                        ExpenseFormActions(
                            onNameChanged = viewModel::onNameChanged,
                            onAmountChanged = viewModel::onAmountChanged,
                            onCategorySelected = viewModel::onCategorySelected,
                            onDateSelected = viewModel::onDateSelected,
                            onNoteChanged = viewModel::onNoteChanged,
                        ),
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
        OutlinedTextField(
            value = form.name,
            onValueChange = actions.onNameChanged,
            label = { Text(stringResource(R.string.expense_field_name)) },
            isError = form.nameError != null,
            supportingText = { form.nameError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.amountText,
            onValueChange = actions.onAmountChanged,
            label = { Text(stringResource(R.string.field_amount)) },
            isError = form.amountError != null,
            supportingText = { form.amountError?.let { Text(it) } },
            singleLine = true,
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
