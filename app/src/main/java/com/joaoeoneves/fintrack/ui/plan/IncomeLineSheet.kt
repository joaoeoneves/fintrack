package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.MAX_PLAN_LABEL_LENGTH
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.ui.common.ConfirmRemoveDialog
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.MoneyTextField
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth

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
    var label by remember { mutableStateOf(editor.initialLabel) }
    var amountCents by remember { mutableLongStateOf(editor.initialAmountCents ?: 0L) }
    var amountError by remember { mutableStateOf(false) }
    var schedule by remember { mutableStateOf(editor.initialSchedule) }
    // The VM's label error is shown only while the text still equals the last submitted text.
    var submittedLabel by remember { mutableStateOf<String?>(null) }
    val labelModel =
        TextFieldModel(
            value = label,
            error = editor.labelError?.takeIf { label.trim() == submittedLabel }?.asString(),
            onChange = { label = limitInput(label, it, MAX_PLAN_LABEL_LENGTH) },
        )
    val amountModel =
        AmountModel(
            cents = amountCents,
            error = if (amountError) stringResource(R.string.error_invalid_amount) else null,
            onChange = {
                amountCents = it
                amountError = false
            },
        )
    val preview = incomePreview(schedule.basis, amountCents, workingDays.days)
    FinTrackBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(incomeLineTitle(editor.editingId)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimens.Space12),
        )
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            SheetFields(
                editor = editor,
                schedule = schedule,
                onScheduleChange = { schedule = it },
                models = FieldModels(labelModel, amountModel, preview),
                fallbackMonth = workingDays.month,
            )
        }
        PrimaryPillButton(
            text = stringResource(R.string.action_save),
            onClick = {
                submittedLabel = label.trim()
                if (editor.managed) {
                    onSave(editor.initialLabel, editor.initialAmountCents ?: 0L, schedule)
                } else {
                    if (amountCents > 0L) onSave(label, amountCents, schedule) else amountError = true
                }
            },
            modifier = Modifier.padding(top = Dimens.Space16),
        )
        if (!editor.managed) RemoveLineButton(editor.editingId, editor.initialLabel, onDelete)
    }
}

@Composable
private fun ColumnScope.RemoveLineButton(
    editingId: String?,
    label: String,
    onDelete: (String) -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    if (editingId != null) {
        DestructiveTextButton(
            text = stringResource(R.string.action_remove),
            onClick = { confirming = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
    if (confirming && editingId != null) {
        ConfirmRemoveDialog(
            title = stringResource(R.string.confirm_remove_title, label),
            message = stringResource(R.string.income_line_remove_message),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                confirming = false
                onDelete(editingId)
            },
            onDismiss = { confirming = false },
        )
    }
}

/** The component name of a managed line (its stored label when it belongs to none). */
@Composable
private fun IncomeLineEditorState.managedName(): String {
    val salaryComponent = component?.takeIf { it != SalaryComponent.EXTRA }
    return salaryComponent?.label() ?: initialLabel
}

private fun incomeLineTitle(editingId: String?): Int =
    if (editingId == null) R.string.plan_income_line_new else R.string.plan_income_line_edit

/** A text field's value, its validation error (if any) and its edit callback. */
private data class TextFieldModel(
    val value: String,
    val error: String?,
    val onChange: (String) -> Unit,
)

/** The money field's cents, its validation error (if any) and its edit callback. */
private data class AmountModel(
    val cents: Long,
    val error: String?,
    val onChange: (Long) -> Unit,
)

private class FieldModels(
    val label: TextFieldModel,
    val amount: AmountModel,
    val preview: String?,
)

@Composable
private fun SheetFields(
    editor: IncomeLineEditorState,
    schedule: IncomeSchedule,
    onScheduleChange: (IncomeSchedule) -> Unit,
    models: FieldModels,
    fallbackMonth: YearMonth,
) {
    if (editor.managed) {
        ManagedLineFields(
            name = editor.managedName(),
            amountCents = editor.initialAmountCents ?: 0L,
        )
    } else {
        IncomeLineFields(
            perDay = schedule.basis == IncomeBasis.PER_WORKING_DAY,
            label = models.label,
            amount = models.amount,
        )
        IncomeScheduleFields(
            schedule = schedule,
            onChange = onScheduleChange,
            fallbackMonth = fallbackMonth,
            preview = models.preview,
        )
    }
}

/**
 * The fields of a line generated from the salary profile: its read-only component name and amount, a hint to
 * change it in the profile.
 */
@Composable
private fun ManagedLineFields(
    name: String,
    amountCents: Long,
) {
    SettingsRow(title = stringResource(R.string.plan_field_label), value = name)
    SettingsRow(title = stringResource(R.string.plan_field_amount), value = formatAmountCents(amountCents))
    Text(
        text = stringResource(R.string.plan_managed_line_hint),
        style = MaterialTheme.typography.bodySmall,
        color = FinTrackTheme.colors.textSecondary,
    )
}

/** The "x N working days = amount" line for per-working-day lines; `null` for fixed ones. */
@Composable
private fun incomePreview(
    basis: IncomeBasis,
    rateCents: Long,
    days: Int,
): String? {
    if (basis != IncomeBasis.PER_WORKING_DAY) return null
    return pluralStringResource(R.plurals.plan_income_preview, days, days, formatAmountCents(rateCents * days))
}

@Composable
private fun IncomeLineFields(
    perDay: Boolean,
    label: TextFieldModel,
    amount: AmountModel,
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
        MoneyTextField(
            cents = amount.cents,
            onCentsChange = amount.onChange,
            label = stringResource(if (perDay) R.string.plan_field_amount_per_day else R.string.plan_field_amount),
            isError = amount.error != null,
            supportingText = amount.error,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
