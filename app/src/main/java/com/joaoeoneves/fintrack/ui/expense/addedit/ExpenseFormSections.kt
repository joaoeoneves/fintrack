package com.joaoeoneves.fintrack.ui.expense.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.MAX_SPLIT_PARTS
import com.joaoeoneves.fintrack.ui.common.CategoryChipFlow
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.FormSaveError
import com.joaoeoneves.fintrack.ui.common.SearchField
import com.joaoeoneves.fintrack.ui.common.amountMatchesQuery
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.containsIgnoreCase
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.MoneyTextField
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

// The refund-link and split sections of the expense form.

/** The "Refund of" row: the linked original (or "None"), tapping it opens the picker sheet. */
@Composable
internal fun RefundOfRow(
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

/** On an existing, non-refund expense: how much of it was refunded (text only). */
@Composable
internal fun RefundStatusLine(form: ExpenseFormState) {
    if (!form.isEditMode || form.isRefund) return
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
}

private val RefundSheetMaxHeight = 360.dp

/**
 * New expenses and parts of an existing split: a "More options" section holding the split switch (hidden for
 * refunds); a stored split shows the switch straight away.
 */
@Composable
internal fun MoreOptions(
    form: ExpenseFormState,
    actions: ExpenseFormActions,
) {
    val editsSplit = form.isEditMode && form.splitId != null
    if ((form.isEditMode && !editsSplit) || form.isRefund) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (!expanded && !form.splitEnabled && !editsSplit) {
        GhostButton(
            text = stringResource(R.string.expense_more_options),
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        SettingsSwitchRow(
            title = stringResource(R.string.expense_split_switch),
            checked = form.splitEnabled,
            onCheckedChange = actions.split.onEnabledChanged,
        )
    }
}

/** The parts of a split: a category and an amount each, add/remove, and what is left of the total. */
@Composable
internal fun SplitParts(
    form: ExpenseFormState,
    categoryOptions: List<Category>,
    actions: SplitActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space16)) {
        form.splitParts.forEachIndexed { index, part ->
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
                CategoryChipFlow(
                    options = categoryOptions,
                    selected = part.category,
                    error = null,
                    onSelected = { actions.onCategoryChanged(index, it) },
                )
                MoneyTextField(
                    cents = part.amountCents,
                    onCentsChange = { actions.onAmountChanged(index, it) },
                    label = stringResource(R.string.field_amount),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (form.splitParts.size > 2) {
                    DestructiveTextButton(
                        text = stringResource(R.string.action_delete),
                        onClick = { actions.onRemoved(index) },
                    )
                }
            }
        }
        if (form.splitParts.size < MAX_SPLIT_PARTS) {
            GhostButton(
                text = stringResource(R.string.expense_split_add_part),
                onClick = actions.onAdded,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val remaining = form.splitRemainingCents
        if (remaining != 0L) {
            Text(
                text =
                    if (remaining > 0) {
                        stringResource(R.string.expense_split_left, formatAmountCents(remaining))
                    } else {
                        stringResource(R.string.expense_split_over, formatAmountCents(-remaining))
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = FinTrackTheme.colors.textSecondary,
            )
        }
        form.splitError?.let { FormSaveError(it.asString(), Modifier.fillMaxWidth()) }
    }
}
