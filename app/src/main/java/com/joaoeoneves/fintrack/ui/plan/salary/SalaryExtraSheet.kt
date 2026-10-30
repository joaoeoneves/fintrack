package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MAX_EXTRA_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountScope
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryExtraDeductions
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** Bottom sheet to add or edit one extra; the tax choices and the fee only exist in gross mode. */
@Composable
internal fun SalaryExtraSheet(
    editor: SalaryExtraEditorState,
    actions: SalaryExtraActions,
) {
    val form = editor.form
    val gross = editor.mode == SalaryInputMode.GROSS
    val onChange = actions.onChange
    FinTrackBottomSheet(onDismissRequest = actions.onDismiss) {
        Text(
            text = stringResource(if (editor.isNew) R.string.salary_extra_new else R.string.salary_extra_edit),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimens.Space12),
        )
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            MinimalTextField(
                value = form.name,
                onValueChange = { onChange(form.copy(name = limitInput(form.name, it, MAX_EXTRA_NAME_LENGTH))) },
                label = stringResource(R.string.salary_extra_name),
                supportingText = {
                    FieldSupportingText(error = null, length = form.name.codePointLength(), max = MAX_EXTRA_NAME_LENGTH)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (gross) {
                SegmentedTextTabs(
                    options = SalaryExtraAmountType.entries,
                    selected = form.amountType,
                    label = { stringResource(typeLabel(it)) },
                    onSelect = { onChange(form.copy(amountType = it, amountText = "", amountCents = 0L)) },
                )
            }
            AmountAndMonthsFields(editor, onChange)
            if (gross) GrossOnlyFields(editor, onChange)
        }
        PrimaryPillButton(
            text = stringResource(R.string.action_save),
            onClick = actions.onSave,
            enabled = editor.canSave,
            modifier = Modifier.padding(top = Dimens.Space16),
        )
        form.editingId?.let { id ->
            DestructiveTextButton(
                text = stringResource(R.string.salary_extra_delete),
                onClick = { actions.onDelete(id) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun AmountAndMonthsFields(
    editor: SalaryExtraEditorState,
    onChange: (SalaryExtraForm) -> Unit,
) {
    val form = editor.form
    val gross = editor.mode == SalaryInputMode.GROSS
    val perPayment = form.months.size > 1 && form.amountScope == SalaryExtraAmountScope.PER_PAYMENT
    val type = if (gross) form.amountType else SalaryExtraAmountType.FIXED_CENTS
    val label = stringResource(valueLabel(type, perPayment))
    if (type == SalaryExtraAmountType.FIXED_CENTS) {
        SalaryMoneyField(label = label, cents = form.amountCents, onChange = { onChange(form.copy(amountCents = it)) })
    } else {
        SalaryTextField(
            label = label,
            value = form.amountText,
            onChange = { onChange(form.copy(amountText = it)) },
            invalid = SalaryExtraField.AMOUNT in editor.invalid && form.amountText.isNotBlank(),
        )
    }
    SalaryMonthPicker(
        title = stringResource(R.string.salary_extra_months),
        selected = form.months,
        onChange = { onChange(form.copy(months = it)) },
    )
    if (form.months.size > 1) {
        SegmentedTextTabs(
            options = SalaryExtraAmountScope.entries,
            selected = form.amountScope,
            label = { stringResource(scopeLabel(it)) },
            onSelect = { onChange(form.copy(amountScope = it)) },
        )
    }
}

@Composable
private fun GrossOnlyFields(
    editor: SalaryExtraEditorState,
    onChange: (SalaryExtraForm) -> Unit,
) {
    val form = editor.form
    Text(
        text = stringResource(R.string.salary_extra_deductions),
        style = MaterialTheme.typography.labelLarge,
        color = FinTrackTheme.colors.textSecondary,
    )
    SegmentedTextTabs(
        options = SalaryExtraDeductions.entries,
        selected = form.deductions,
        label = { stringResource(deductionsLabel(it)) },
        onSelect = { onChange(form.copy(deductions = it)) },
    )
    AdvancedSection(initiallyExpanded = form.feeText.isNotBlank()) {
        SalaryTextField(
            label = stringResource(R.string.salary_extra_fee),
            value = form.feeText,
            onChange = { onChange(form.copy(feeText = it)) },
            invalid = SalaryExtraField.FEE in editor.invalid,
        )
    }
}

private fun typeLabel(type: SalaryExtraAmountType): Int =
    when (type) {
        SalaryExtraAmountType.FIXED_CENTS -> R.string.salary_extra_type_fixed
        SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM -> R.string.salary_extra_type_multiple
        SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM -> R.string.salary_extra_type_percent
    }

private fun scopeLabel(scope: SalaryExtraAmountScope): Int =
    when (scope) {
        SalaryExtraAmountScope.TOTAL -> R.string.salary_extra_scope_total
        SalaryExtraAmountScope.PER_PAYMENT -> R.string.salary_extra_scope_per_payment
    }

private fun valueLabel(
    type: SalaryExtraAmountType,
    perPayment: Boolean,
): Int =
    when (type) {
        SalaryExtraAmountType.FIXED_CENTS ->
            if (perPayment) R.string.salary_extra_value_fixed_per_payment else R.string.salary_extra_value_fixed
        SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM ->
            if (perPayment) R.string.salary_extra_value_multiple_per_payment else R.string.salary_extra_value_multiple
        SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM ->
            if (perPayment) R.string.salary_extra_value_percent_per_payment else R.string.salary_extra_value_percent
    }

private fun deductionsLabel(deductions: SalaryExtraDeductions): Int =
    when (deductions) {
        SalaryExtraDeductions.NONE -> R.string.salary_extra_deductions_none
        SalaryExtraDeductions.IRS -> R.string.salary_extra_deductions_irs
        SalaryExtraDeductions.IRS_SS -> R.string.salary_extra_deductions_irs_ss
    }
