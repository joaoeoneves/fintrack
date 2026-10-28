package com.joaoeoneves.fintrack.ui.plan.goals

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.ReadOnlyDateField
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChip
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.MoneyTextField
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Name, target amount and the ANNUAL / TOTAL choice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalBasicFields(
    draft: GoalDraft,
    onChange: (GoalDraft) -> Unit,
) {
    MinimalTextField(
        value = draft.name,
        onValueChange = { onChange(draft.copy(name = limitInput(draft.name, it, MAX_GOAL_NAME_LENGTH))) },
        label = stringResource(R.string.goal_field_name),
        isError = draft.showErrors && !draft.nameValid,
        supportingText = {
            FieldSupportingText(
                error = stringResource(R.string.error_name_required).takeIf { draft.showErrors && !draft.nameValid },
                length = draft.name.codePointLength(),
                max = MAX_GOAL_NAME_LENGTH,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    val targetInvalid = draft.showErrors && draft.targetCents <= 0L
    MoneyTextField(
        cents = draft.targetCents,
        onCentsChange = { onChange(draft.copy(targetCents = it)) },
        label = stringResource(R.string.goal_field_target),
        isError = targetInvalid,
        supportingText = stringResource(R.string.error_invalid_amount).takeIf { targetInvalid },
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
        GoalTargetType.entries.forEach { type ->
            CategoryChip(
                label = stringResource(type.labelRes()),
                selected = draft.type == type,
                onClick = { onChange(draft.copy(type = type)) },
            )
        }
    }
}

/** What only a TOTAL goal has: the start date, an optional target date and the amount already saved. */
@Composable
internal fun GoalTotalFields(
    draft: GoalDraft,
    onChange: (GoalDraft) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    ReadOnlyDateField(
        date = draft.startDate.toNoonInstant(zone),
        onDateSelected = { onChange(draft.copy(startDate = it.atZone(zone).toLocalDate())) },
        modifier = Modifier.fillMaxWidth(),
        zone = zone,
        labelRes = R.string.recurring_starts,
    )
    val target = draft.targetDate
    if (target == null) {
        GhostButton(
            text = stringResource(R.string.goal_target_date_add),
            onClick = { onChange(draft.copy(targetDate = draft.startDate.plusYears(1))) },
        )
    } else {
        ReadOnlyDateField(
            date = target.toNoonInstant(zone),
            onDateSelected = { onChange(draft.copy(targetDate = it.atZone(zone).toLocalDate())) },
            modifier = Modifier.fillMaxWidth(),
            zone = zone,
            labelRes = R.string.goal_field_target_date,
        )
        if (draft.showErrors && !draft.datesValid) {
            Text(stringResource(R.string.goal_error_dates), color = FinTrackTheme.colors.negative)
        }
        GhostButton(
            text = stringResource(R.string.goal_target_date_clear),
            onClick = { onChange(draft.copy(targetDate = null)) },
        )
    }
    MoneyTextField(
        cents = draft.initialCents,
        onCentsChange = { onChange(draft.copy(initialCents = it)) },
        label = stringResource(R.string.goal_field_initial),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Multi-select chips for the EXPENSE recurring items, and a category picker (None or one category). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalLinkFields(
    draft: GoalDraft,
    options: GoalEditorOptions,
    onChange: (GoalDraft) -> Unit,
) {
    if (options.recurring.isNotEmpty()) {
        FieldLabel(R.string.goal_link_recurring)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            options.recurring.forEach { item ->
                CategoryChip(
                    label = item.name,
                    selected = item.id in draft.linkedRecurringIds,
                    onClick = { onChange(draft.toggled(item.id)) },
                )
            }
        }
    }
    FieldLabel(R.string.goal_link_category)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        CategoryChip(
            label = stringResource(R.string.goal_link_category_none),
            selected = draft.linkedCategoryId == null,
            onClick = { onChange(draft.copy(linkedCategoryId = null)) },
        )
        options.categories.forEach { category ->
            CategoryChip(
                label = category.displayName,
                selected = draft.linkedCategoryId == category.id.value,
                onClick = { onChange(draft.copy(linkedCategoryId = category.id.value)) },
            )
        }
    }
}

@Composable
private fun FieldLabel(
    @StringRes textRes: Int,
) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelLarge,
        color = FinTrackTheme.colors.textSecondary,
    )
}

private fun GoalTargetType.labelRes(): Int =
    when (this) {
        GoalTargetType.ANNUAL -> R.string.goal_type_annual
        GoalTargetType.TOTAL -> R.string.goal_type_total
    }

private fun LocalDate.toNoonInstant(zone: ZoneId) = atTime(LocalTime.NOON).atZone(zone).toInstant()
