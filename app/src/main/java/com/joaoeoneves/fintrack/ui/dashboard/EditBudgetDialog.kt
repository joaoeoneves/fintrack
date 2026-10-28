package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.ConfirmRemoveDialog
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.expense.addedit.decimalSeparatorFor
import com.joaoeoneves.fintrack.ui.expense.addedit.parseAmountCents
import com.joaoeoneves.fintrack.ui.expense.addedit.toAmountInputText
import com.joaoeoneves.fintrack.ui.plan.MonthChips
import com.joaoeoneves.fintrack.ui.theme.Dimens

@Composable
internal fun EditBudgetDialog(
    budget: CategoryBudget,
    onSave: (Long, Set<Int>) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val decimalSeparator = decimalSeparatorFor(currentLocale())
    var amountText by remember { mutableStateOf(budget.limitCents?.toAmountInputText(decimalSeparator).orEmpty()) }
    var skipMonths by remember { mutableStateOf(budget.skipMonths) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidAmountMessage = stringResource(R.string.error_invalid_amount)
    val hasLimit = budget.limitCents != null
    var confirmingRemove by remember { mutableStateOf(false) }

    FinTrackBottomSheet(onDismissRequest = onDismiss) {
        BudgetSheetTitle(budget.category.displayName)
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            MinimalTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    error = null
                },
                label = stringResource(R.string.budget_dialog_limit_label),
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            SkipMonthChips(skipMonths) { skipMonths = it }
        }
        PrimaryPillButton(
            text = stringResource(R.string.action_save),
            onClick = {
                // Clearing the field means "no limit": remove an existing one (after confirming), otherwise
                // there is nothing to change.
                if (amountText.isBlank()) {
                    if (hasLimit) confirmingRemove = true else onDismiss()
                } else {
                    parseAmountCents(amountText)
                        .onSuccess { cents -> onSave(cents, skipMonths) }
                        .onFailure { error = invalidAmountMessage }
                }
            },
            modifier = Modifier.padding(top = Dimens.Space16),
        )
        if (hasLimit) {
            DestructiveTextButton(
                text = stringResource(R.string.action_remove),
                onClick = { confirmingRemove = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
    if (confirmingRemove) {
        ConfirmRemoveDialog(
            title = stringResource(R.string.budget_remove_title, budget.category.displayName),
            message = stringResource(R.string.budget_remove_message),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = onRemove,
            onDismiss = { confirmingRemove = false },
        )
    }
}

@Composable
private fun BudgetSheetTitle(categoryName: String) {
    Text(
        text = stringResource(R.string.budget_dialog_title, categoryName),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = Dimens.Space12),
    )
}

@Composable
private fun SkipMonthChips(
    skipMonths: Set<Int>,
    onChange: (Set<Int>) -> Unit,
) {
    MonthChips(
        title = stringResource(R.string.plan_not_in),
        selected = skipMonths,
        onToggle = { onChange(if (it in skipMonths) skipMonths - it else skipMonths + it) },
    )
}
