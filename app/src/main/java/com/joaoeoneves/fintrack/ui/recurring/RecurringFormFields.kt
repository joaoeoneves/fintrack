package com.joaoeoneves.fintrack.ui.recurring

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.ReadOnlyDateField
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.displayName
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

@Composable
internal fun RecurringTypeAndNameFields(
    form: RecurringForm,
    errors: RecurringFormErrors?,
    onChange: (RecurringForm) -> Unit,
) {
    ChoiceRow(
        selected = form.type,
        choices =
            listOf(
                Choice(RecurringType.EXPENSE, stringResource(R.string.recurring_type_expense)),
                Choice(RecurringType.INCOME, stringResource(R.string.recurring_type_income)),
            ),
        onSelected = { onChange(form.copy(type = it)) },
    )
    OutlinedTextField(
        value = form.name,
        onValueChange = { onChange(form.copy(name = it)) },
        label = { Text(stringResource(R.string.expense_field_name)) },
        isError = errors?.name != null,
        supportingText = {
            FieldSupportingText(
                error = errors?.name?.asString(),
                length = form.name.codePointLength(),
                max = MAX_NAME_LENGTH,
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.amountText,
        onValueChange = { onChange(form.copy(amountText = it)) },
        label = { Text(stringResource(R.string.field_amount)) },
        isError = errors?.amount != null,
        supportingText = { errors?.amount?.let { Text(it.asString()) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
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
    ChoiceDropdown(
        label = stringResource(R.string.expense_field_category),
        selectedLabel = form.category?.displayName.orEmpty(),
        choices = options.map { Choice(it, it.displayName) },
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
    OutlinedTextField(
        value = form.dayText,
        onValueChange = { onChange(form.copy(dayText = it.filter(Char::isDigit).take(DAY_MAX_DIGITS))) },
        label = { Text(stringResource(R.string.recurring_day_of_month)) },
        isError = errors?.day != null,
        supportingText = { errors?.day?.let { Text(it.asString()) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
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
        TextButton(onClick = { onChange(form.copy(endDate = form.startDate)) }) {
            Text(stringResource(R.string.recurring_end_date_add))
        }
    } else {
        ReadOnlyDateField(
            date = endDate.toNoonInstant(zone),
            onDateSelected = { onChange(form.copy(endDate = it.atZone(zone).toLocalDate())) },
            modifier = Modifier.fillMaxWidth(),
            zone = zone,
            labelRes = R.string.recurring_end_date,
        )
        errors?.endDate?.let { Text(it.asString()) }
        TextButton(onClick = { onChange(form.copy(endDate = null)) }) {
            Text(stringResource(R.string.recurring_end_date_clear))
        }
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
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice.value == selected,
                onClick = { onSelected(choice.value) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
            ) { Text(choice.label) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceDropdown(
    label: String,
    selectedLabel: String,
    choices: List<Choice<T>>,
    error: String?,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            readOnly = true,
            value = selectedLabel,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
}
