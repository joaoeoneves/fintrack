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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.ui.common.displayName
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val displayDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC)

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
            val isEditMode = (uiState as? AddEditUiState.Ready)?.form?.isEditMode ?: false
            TopAppBar(
                title = { Text(if (isEditMode) "Edit expense" else "Add expense") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    val form = (uiState as? AddEditUiState.Ready)?.form
                    TextButton(
                        onClick = { viewModel.onSave() },
                        enabled = form?.isValid == true,
                    ) {
                        Text("Save")
                    }
                },
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
                    onNameChanged = viewModel::onNameChanged,
                    onAmountChanged = viewModel::onAmountChanged,
                    onCategorySelected = viewModel::onCategorySelected,
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
private fun ExpenseForm(
    form: ExpenseFormState,
    onNameChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onCategorySelected: (ExpenseCategory) -> Unit,
    onDateSelected: (Instant) -> Unit,
    onNoteChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = onNameChanged,
            label = { Text("Name") },
            isError = form.nameError != null,
            supportingText = { form.nameError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.amountText,
            onValueChange = onAmountChanged,
            label = { Text("Amount") },
            isError = form.amountError != null,
            supportingText = { form.amountError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(
            expanded = categoryMenuExpanded,
            onExpandedChange = { categoryMenuExpanded = it },
        ) {
            OutlinedTextField(
                readOnly = true,
                value = form.category.displayName,
                onValueChange = {},
                label = { Text("Category") },
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
                ExpenseCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.displayName) },
                        onClick = {
                            onCategorySelected(category)
                            categoryMenuExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            readOnly = true,
            value = displayDateFormatter.format(form.date),
            onValueChange = {},
            label = { Text("Date") },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Change date")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.note,
            onValueChange = onNoteChanged,
            label = { Text("Note (optional)") },
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
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
