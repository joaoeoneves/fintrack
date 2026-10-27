package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.expense.addedit.decimalSeparatorFor
import com.joaoeoneves.fintrack.ui.expense.addedit.parseAmountCents
import com.joaoeoneves.fintrack.ui.expense.addedit.toAmountInputText

@Composable
internal fun EditBudgetDialog(
    budget: CategoryBudget,
    onSave: (Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val decimalSeparator = decimalSeparatorFor(currentLocale())
    var amountText by remember { mutableStateOf(budget.limitCents?.toAmountInputText(decimalSeparator).orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidAmountMessage = stringResource(R.string.error_invalid_amount)
    val hasLimit = budget.limitCents != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_dialog_title, budget.category.displayName)) },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    error = null
                },
                label = { Text(stringResource(R.string.budget_dialog_limit_label)) },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    // Clearing the field means "no limit": remove an existing one, otherwise
                    // there is nothing to change.
                    amountText.isBlank() -> if (hasLimit) onRemove() else onDismiss()
                    else ->
                        parseAmountCents(amountText)
                            .onSuccess { cents -> onSave(cents) }
                            .onFailure { error = invalidAmountMessage }
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (hasLimit) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.action_remove))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}
