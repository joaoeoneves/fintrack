package com.joaoeoneves.fintrack.ui.expense.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.model.NameSuggestion
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.optionsIncluding
import com.joaoeoneves.fintrack.ui.common.CategoryChipFlow
import com.joaoeoneves.fintrack.ui.common.DeletedEffect
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.FormDeleteButton
import com.joaoeoneves.fintrack.ui.common.FormSaveError
import com.joaoeoneves.fintrack.ui.common.FormScaffold
import com.joaoeoneves.fintrack.ui.common.ReadOnlyDateField
import com.joaoeoneves.fintrack.ui.common.SearchField
import com.joaoeoneves.fintrack.ui.common.amountMatchesQuery
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.containsIgnoreCase
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.rememberCurrencyAffix
import com.joaoeoneves.fintrack.ui.designsystem.BorderlessAmountField
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChip
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import com.joaoeoneves.fintrack.ui.designsystem.rememberIsDirty
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant

@Suppress("LongParameterList") // One navigation callback per exit of the form.
@Composable
fun AddEditExpenseScreen(
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onRecordRefund: (String) -> Unit = {},
    viewModel: AddEditExpenseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categoryOptions by viewModel.categoryOptions.collectAsStateWithLifecycle()
    val readyForm = (uiState as? AddEditUiState.Ready)?.form

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collectLatest { onSaved() }
    }
    DeletedEffect(viewModel.deletedEvent, onDeleted)

    FormScaffold(
        title = stringResource(if (viewModel.isEditRoute) R.string.expense_edit_title else R.string.action_add_expense),
        onCancel = onCancel,
        saveEnabled = readyForm != null && !readyForm.isBusy,
        onSave = viewModel::onSave,
        showSave = readyForm != null,
        dirty = rememberIsDirty(readyForm?.untouchedByErrors()),
        modifier = modifier,
    ) { innerPadding ->
        when (val state = uiState) {
            is AddEditUiState.Loading -> Unit

            is AddEditUiState.Ready -> {
                ExpenseForm(
                    form = state.form,
                    categoryOptions = categoryOptions.optionsIncluding(state.form.category),
                    isEditMode = viewModel.isEditRoute,
                    actions = viewModel.formActions(onRecordRefund),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                )
            }

            is AddEditUiState.Error -> {
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

/** Bundles [ExpenseForm]'s field-change callbacks to keep its own parameter list short. */
private data class ExpenseFormActions(
    val onNameChanged: (String) -> Unit,
    val onNameSuggestionPicked: (NameSuggestion) -> Unit,
    val onAmountChanged: (Long) -> Unit,
    val onCategorySelected: (Category) -> Unit,
    val onDateSelected: (Instant) -> Unit,
    val onNoteChanged: (String) -> Unit,
    val onRefundChanged: (Boolean) -> Unit,
    val onRefundOfSelected: (Expense?) -> Unit,
    val onRecordRefund: (String) -> Unit,
    val onMealCardChanged: (Boolean) -> Unit,
    val onDelete: () -> Unit,
)

private fun AddEditExpenseViewModel.formActions(onRecordRefund: (String) -> Unit): ExpenseFormActions =
    ExpenseFormActions(
        onNameChanged = ::onNameChanged,
        onNameSuggestionPicked = ::onNameSuggestionPicked,
        onAmountChanged = ::onAmountChanged,
        onCategorySelected = ::onCategorySelected,
        onDateSelected = ::onDateSelected,
        onNoteChanged = ::onNoteChanged,
        onRefundChanged = ::onRefundChanged,
        onRefundOfSelected = ::onRefundOfSelected,
        onRecordRefund = onRecordRefund,
        onMealCardChanged = ::onMealCardChanged,
        onDelete = ::onDelete,
    )

@Composable
private fun ExpenseForm(
    form: ExpenseFormState,
    categoryOptions: List<Category>,
    isEditMode: Boolean,
    actions: ExpenseFormActions,
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
            value = form.name,
            onValueChange = actions.onNameChanged,
            label = stringResource(R.string.expense_field_name),
            isError = form.nameError != null,
            supportingText = {
                FieldSupportingText(
                    error = form.nameError?.asString(),
                    length = form.name.codePointLength(),
                    max = MAX_NAME_LENGTH,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        NameSuggestionRow(form.nameSuggestions, actions.onNameSuggestionPicked)

        CategoryChipFlow(
            options = categoryOptions,
            selected = form.category,
            error = form.categoryError?.asString(),
            onSelected = actions.onCategorySelected,
        )
        if (form.suggestedCategory) {
            Text(
                text = stringResource(R.string.expense_category_suggested),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        RefundAndMealCardSwitches(form = form, actions = actions)
        RecordRefundSection(form = form, actions = actions)

        ExpenseDateAndNoteFields(form = form, actions = actions)

        form.saveError?.let { FormSaveError(it.asString()) }
        if (form.canDelete) FormDeleteButton(onClick = actions.onDelete, enabled = !form.isBusy)
    }
}

/** The earlier names matching what is typed, one tap each; shows nothing when there are none. */
@Composable
private fun NameSuggestionRow(
    suggestions: List<NameSuggestion>,
    onPicked: (NameSuggestion) -> Unit,
) {
    if (suggestions.isEmpty()) return
    val label = stringResource(R.string.expense_name_suggestions)
    FlowRow(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        suggestions.forEach { suggestion ->
            CategoryChip(label = suggestion.name, selected = false, onClick = { onPicked(suggestion) })
        }
    }
}

@Composable
private fun RefundAndMealCardSwitches(
    form: ExpenseFormState,
    actions: ExpenseFormActions,
) {
    SettingsSwitchRow(
        title = stringResource(R.string.expense_field_refund),
        checked = form.isRefund,
        onCheckedChange = actions.onRefundChanged,
        supportingText = stringResource(R.string.expense_field_refund_hint),
    )
    if (form.isRefund) RefundOfRow(form = form, actions = actions)
    if (form.showsMealCardSwitch) {
        SettingsSwitchRow(
            title = stringResource(R.string.expense_field_meal_card),
            checked = form.paidWithMealCard,
            onCheckedChange = actions.onMealCardChanged,
        )
    }
}

@Composable
private fun ExpenseDateAndNoteFields(
    form: ExpenseFormState,
    actions: ExpenseFormActions,
) {
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
}

/** The form without its transient validation/saving state, so only the user's edits count as changes. */
private fun ExpenseFormState.untouchedByErrors() =
    copy(
        nameError = null,
        amountError = null,
        categoryError = null,
        saveError = null,
        isSaving = false,
        isDeleting = false,
        refundStatus = null,
        refundCandidates = emptyList(),
    )

/** The "Refund of" row: the linked original (or "None"), tapping it opens the picker sheet. */
@Composable
private fun RefundOfRow(
    form: ExpenseFormState,
    actions: ExpenseFormActions,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        title = stringResource(R.string.expense_refund_of),
        value = form.refundOfLabel ?: stringResource(R.string.expense_refund_of_none),
        onClick = { picking = true },
        showChevron = true,
    )
    if (picking) {
        RefundOfSheet(
            candidates = form.refundCandidates,
            onPicked = {
                actions.onRefundOfSelected(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** Search, then pick the original expense this refund is for; the first row unlinks. */
@Composable
private fun RefundOfSheet(
    candidates: List<Expense>,
    onPicked: (Expense?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown =
        remember(candidates, query) {
            if (query.isBlank()) {
                candidates
            } else {
                candidates.filter {
                    it.name.containsIgnoreCase(query.trim()) || amountMatchesQuery(it.amountCents, query)
                }
            }
        }
    FinTrackBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.expense_refund_of),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimens.Space12),
        )
        SearchField(
            query = query,
            onQueryChanged = { query = it },
            label = stringResource(R.string.activity_search_label),
        )
        LazyColumn(modifier = Modifier.heightIn(max = RefundSheetMaxHeight)) {
            item(key = "none") {
                SettingsRow(
                    title = stringResource(R.string.expense_refund_of_none),
                    onClick = { onPicked(null) },
                    modifier = Modifier.padding(vertical = Dimens.Space4),
                )
            }
            items(shown, key = { it.id }) { expense ->
                ExpenseRow(expense = expense, onClick = { onPicked(expense) })
            }
        }
    }
}

/** On an existing, non-refund expense: how much of it was refunded, and a shortcut to record another refund. */
@Composable
private fun RecordRefundSection(
    form: ExpenseFormState,
    actions: ExpenseFormActions,
) {
    val id = form.expenseId
    if (!form.isEditMode || form.isRefund || id == null) return
    form.refundStatus?.let { status ->
        Text(
            text =
                stringResource(
                    R.string.expense_refund_status,
                    status.percent,
                    formatAmountCents(status.netCents),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = FinTrackTheme.colors.textSecondary,
        )
    }
    GhostButton(
        text = stringResource(R.string.expense_record_refund),
        onClick = { actions.onRecordRefund(id) },
        enabled = !form.isBusy,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val RefundSheetMaxHeight = 360.dp
