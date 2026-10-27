package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.MAX_PLAN_LABEL_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.expense.addedit.decimalSeparatorFor
import com.joaoeoneves.fintrack.ui.expense.addedit.parseAmountCents
import com.joaoeoneves.fintrack.ui.expense.addedit.toAmountInputText
import com.joaoeoneves.fintrack.ui.theme.Dimens

/**
 * Bottom sheet to add or edit one expected-income line: label, amount, basis (fixed or per working day),
 * frequency and its months. The label is validated by the ViewModel (the result arrives through
 * [IncomeLineEditorState.labelError]); the amount is parsed here. [workingDays] drives the live preview.
 */
@Composable
fun IncomeLineSheet(
    editor: IncomeLineEditorState,
    workingDays: WorkingDaysState,
    onSave: (label: String, amountCents: Long, schedule: IncomeSchedule) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val decimalSeparator = decimalSeparatorFor(currentLocale())
    var label by remember { mutableStateOf(editor.initialLabel) }
    val initialAmount = editor.initialAmountCents?.toAmountInputText(decimalSeparator).orEmpty()
    var amountText by remember { mutableStateOf(initialAmount) }
    var amountError by remember { mutableStateOf(false) }
    var schedule by remember { mutableStateOf(editor.initialSchedule) }
    // The VM's label error is shown only while the text still equals the last submitted text.
    var submittedLabel by remember { mutableStateOf<String?>(null) }
    val editingId = editor.editingId
    val labelModel =
        TextFieldModel(
            value = label,
            error = editor.labelError?.takeIf { label.trim() == submittedLabel }?.asString(),
            onChange = { label = limitInput(label, it, MAX_PLAN_LABEL_LENGTH) },
        )
    val amountModel =
        TextFieldModel(
            value = amountText,
            error = if (amountError) stringResource(R.string.error_invalid_amount) else null,
            onChange = {
                amountText = it
                amountError = false
            },
        )
    FinTrackBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(incomeLineTitle(editingId)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimens.Space12),
        )
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            IncomeLineFields(
                perDay = schedule.basis == IncomeBasis.PER_WORKING_DAY,
                label = labelModel,
                amount = amountModel,
            )
            IncomeScheduleFields(
                schedule = schedule,
                onChange = { schedule = it },
                fallbackMonth = workingDays.month,
                preview = incomePreview(schedule.basis, amountText, workingDays.days),
            )
        }
        PrimaryPillButton(
            text = stringResource(R.string.action_save),
            onClick = {
                submittedLabel = label.trim()
                parseAmountCents(amountText)
                    .onSuccess { cents -> onSave(label, cents, schedule) }
                    .onFailure { amountError = true }
            },
            modifier = Modifier.padding(top = Dimens.Space16),
        )
        RemoveLineButton(editingId, onDelete)
    }
}

@Composable
private fun ColumnScope.RemoveLineButton(
    editingId: String?,
    onDelete: (String) -> Unit,
) {
    if (editingId != null) {
        DestructiveTextButton(
            text = stringResource(R.string.action_remove),
            onClick = { onDelete(editingId) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

private fun incomeLineTitle(editingId: String?): Int =
    if (editingId == null) R.string.plan_income_line_new else R.string.plan_income_line_edit

/** A text field's value, its validation error (if any) and its edit callback. */
private data class TextFieldModel(
    val value: String,
    val error: String?,
    val onChange: (String) -> Unit,
)

/** The "x N working days = amount" line for per-working-day lines; `null` for fixed ones. */
@Composable
private fun incomePreview(
    basis: IncomeBasis,
    amountText: String,
    days: Int,
): String? {
    if (basis != IncomeBasis.PER_WORKING_DAY) return null
    val rate = parseAmountCents(amountText).getOrDefault(0L)
    return pluralStringResource(R.plurals.plan_income_preview, days, days, formatAmountCents(rate * days))
}

@Composable
private fun IncomeLineFields(
    perDay: Boolean,
    label: TextFieldModel,
    amount: TextFieldModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
        MinimalTextField(
            value = label.value,
            onValueChange = label.onChange,
            label = stringResource(R.string.plan_field_label),
            isError = label.error != null,
            supportingText = {
                FieldSupportingText(
                    error = label.error,
                    length = label.value.codePointLength(),
                    max = MAX_PLAN_LABEL_LENGTH,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        MinimalTextField(
            value = amount.value,
            onValueChange = amount.onChange,
            label = stringResource(if (perDay) R.string.plan_field_amount_per_day else R.string.plan_field_amount),
            isError = amount.error != null,
            supportingText = { amount.error?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
