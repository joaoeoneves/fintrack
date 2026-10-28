package com.joaoeoneves.fintrack.ui.recurring

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.MAX_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MIN_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.ui.common.CategoryChipFlow
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.ReadOnlyDateField
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.rememberCurrencyAffix
import com.joaoeoneves.fintrack.ui.common.symbol
import com.joaoeoneves.fintrack.ui.common.symbolLeads
import com.joaoeoneves.fintrack.ui.designsystem.BorderlessAmountField
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle

/** One selectable option of a [ChoiceRow] or [ChoiceDropdown]. */
private data class Choice<T>(
    val value: T,
    val label: String,
)

/** Amount first, then the Type tabs and the name. */
@Composable
internal fun RecurringTypeAndNameFields(
    form: RecurringForm,
    errors: RecurringFormErrors?,
    onChange: (RecurringForm) -> Unit,
    autoFocusAmount: Boolean = false,
) {
    val affix = rememberCurrencyAffix()
    BorderlessAmountField(
        cents = form.amountCents,
        onCentsChange = { onChange(form.copy(amountCents = it)) },
        currencySymbol = affix.symbol,
        symbolLeads = affix.leads,
        label = stringResource(R.string.field_amount),
        isError = errors?.amount != null,
        autoFocus = autoFocusAmount,
        modifier = Modifier.padding(vertical = Dimens.Space16),
    )
    errors?.amount?.let { Text(it.asString(), color = FinTrackTheme.colors.negative) }
    ChoiceRow(
        selected = form.type,
        choices =
            listOf(
                Choice(RecurringType.EXPENSE, stringResource(R.string.recurring_type_expense)),
                Choice(RecurringType.INCOME, stringResource(R.string.recurring_type_income)),
            ),
        onSelected = { onChange(form.copy(type = it)) },
    )
    MinimalTextField(
        value = form.name,
        onValueChange = { onChange(form.copy(name = it)) },
        label = stringResource(R.string.expense_field_name),
        isError = errors?.name != null,
        supportingText = {
            FieldSupportingText(
                error = errors?.name?.asString(),
                length = form.name.codePointLength(),
                max = MAX_NAME_LENGTH,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun RecurringCategoryField(
    form: RecurringForm,
    options: List<Category>,
    errors: RecurringFormErrors?,
    onChange: (RecurringForm) -> Unit,
) {
    CategoryChipFlow(
        options = options,
        selected = form.category,
        error = errors?.category?.asString(),
        onSelected = { onChange(form.copy(category = it)) },
    )
}

@Composable
internal fun RecurringScheduleFields(
    form: RecurringForm,
    errors: RecurringFormErrors?,
    onChange: (RecurringForm) -> Unit,
) {
    ChoiceRow(
        selected = form.frequency,
        choices =
            listOf(
                Choice(RecurringFrequency.MONTHLY, stringResource(R.string.recurring_frequency_monthly)),
                Choice(RecurringFrequency.YEARLY, stringResource(R.string.recurring_frequency_yearly)),
            ),
        onSelected = { onChange(form.copy(frequency = it)) },
    )
    MinimalTextField(
        value = form.dayText,
        onValueChange = { onChange(form.copy(dayText = it.filter(Char::isDigit).take(DAY_MAX_DIGITS))) },
        label = stringResource(R.string.recurring_day_of_month),
        isError = errors?.day != null,
        supportingText = { errors?.day?.let { Text(it.asString()) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    if (form.frequency == RecurringFrequency.YEARLY) {
        val locale = currentLocale()
        val months =
            (MIN_MONTH_OF_YEAR..MAX_MONTH_OF_YEAR).map {
                Choice(it, Month.of(it).getDisplayName(TextStyle.FULL_STANDALONE, locale))
            }
        ChoiceDropdown(
            label = stringResource(R.string.recurring_month),
            selectedLabel = months.firstOrNull { it.value == form.monthOfYear }?.label.orEmpty(),
            choices = months,
            error = errors?.month?.asString(),
            onSelected = { onChange(form.copy(monthOfYear = it)) },
        )
    }
}

@Composable
internal fun RecurringDateFields(
    form: RecurringForm,
    errors: RecurringFormErrors?,
    onChange: (RecurringForm) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    ReadOnlyDateField(
        date = form.startDate.toNoonInstant(zone),
        onDateSelected = { onChange(form.copy(startDate = it.atZone(zone).toLocalDate())) },
        modifier = Modifier.fillMaxWidth(),
        zone = zone,
        labelRes = R.string.recurring_starts,
    )
    val endDate = form.endDate
    if (endDate == null) {
        GhostButton(
            text = stringResource(R.string.recurring_end_date_add),
            onClick = { onChange(form.copy(endDate = form.startDate)) },
        )
    } else {
        ReadOnlyDateField(
            date = endDate.toNoonInstant(zone),
            onDateSelected = { onChange(form.copy(endDate = it.atZone(zone).toLocalDate())) },
            modifier = Modifier.fillMaxWidth(),
            zone = zone,
            labelRes = R.string.recurring_end_date,
        )
        errors?.endDate?.let { Text(it.asString(), color = FinTrackTheme.colors.negative) }
        GhostButton(
            text = stringResource(R.string.recurring_end_date_clear),
            onClick = { onChange(form.copy(endDate = null)) },
        )
    }
}

private const val DAY_MAX_DIGITS = 2

private fun LocalDate.toNoonInstant(zone: ZoneId) = atTime(LocalTime.NOON).atZone(zone).toInstant()

@Composable
private fun <T> ChoiceRow(
    selected: T,
    choices: List<Choice<T>>,
    onSelected: (T) -> Unit,
) {
    SegmentedTextTabs(
        options = choices,
        selected = choices.firstOrNull { it.value == selected },
        label = { it.label },
        onSelect = { onSelected(it.value) },
    )
}

@Composable
private fun <T> ChoiceDropdown(
    label: String,
    selectedLabel: String,
    choices: List<Choice<T>>,
    error: String?,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        SettingsRow(title = label, value = selectedLabel, onClick = { expanded = true }, showChevron = true)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = FinTrackTheme.colors.surface2,
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = {
                        onSelected(choice.value)
                        expanded = false
                    },
                )
            }
        }
    }
    error?.let { Text(it, color = FinTrackTheme.colors.negative) }
}
