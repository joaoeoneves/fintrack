package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Column
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
import com.joaoeoneves.fintrack.domain.model.MAX_PLAN_LABEL_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.expense.addedit.decimalSeparatorFor
import com.joaoeoneves.fintrack.ui.expense.addedit.parseAmountCents
import com.joaoeoneves.fintrack.ui.expense.addedit.toAmountInputText

/**
 * Dialog to add or edit one expected-income line. The label is validated by the ViewModel (the
 * result arrives through [IncomeLineEditorState.labelError]); the amount is parsed here.
 */
@Composable
fun IncomeLineDialog(
    editor: IncomeLineEditorState,
    onSave: (label: String, amountCents: Long) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val decimalSeparator = decimalSeparatorFor(currentLocale())
    var label by remember { mutableStateOf(editor.initialLabel) }
    val initialAmount = editor.initialAmountCents?.toAmountInputText(decimalSeparator).orEmpty()
    var amountText by remember { mutableStateOf(initialAmount) }
    var amountError by remember { mutableStateOf(false) }
    // The VM's label error is shown only while the text still equals the last submitted text.
    var submittedLabel by remember { mutableStateOf<String?>(null) }
    val labelErrorShown = editor.labelError != null && label.trim() == submittedLabel
    val editingId = editor.editingId
    val titleRes = if (editingId == null) R.string.plan_income_line_new else R.string.plan_income_line_edit
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            IncomeLineFields(
                label =
                    TextFieldModel(
                        value = label,
                        error = editor.labelError?.takeIf { labelErrorShown }?.asString(),
                        onChange = {
                            label = limitInput(label, it, MAX_PLAN_LABEL_LENGTH)
                        },
                    ),
                amount =
                    TextFieldModel(
                        value = amountText,
                        error = if (amountError) stringResource(R.string.error_invalid_amount) else null,
                        onChange = {
                            amountText = it
                            amountError = false
                        },
                    ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                submittedLabel = label.trim()
                parseAmountCents(amountText)
                    .onSuccess { cents -> onSave(label, cents) }
                    .onFailure { amountError = true }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (editingId != null) {
                    TextButton(onClick = { onDelete(editingId) }) {
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

/** A text field's value, its validation error (if any) and its edit callback. */
private data class TextFieldModel(
    val value: String,
    val error: String?,
    val onChange: (String) -> Unit,
)

@Composable
private fun IncomeLineFields(
    label: TextFieldModel,
    amount: TextFieldModel,
) {
    Column {
        OutlinedTextField(
            value = label.value,
            onValueChange = label.onChange,
            label = { Text(stringResource(R.string.plan_field_label)) },
            isError = label.error != null,
            supportingText = {
                FieldSupportingText(
                    error = label.error,
                    length = label.value.codePointLength(),
                    max = MAX_PLAN_LABEL_LENGTH,
                )
            },
            singleLine = true,
        )
        OutlinedTextField(
            value = amount.value,
            onValueChange = amount.onChange,
            label = { Text(stringResource(R.string.plan_field_amount)) },
            isError = amount.error != null,
            supportingText = { amount.error?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
    }
}
