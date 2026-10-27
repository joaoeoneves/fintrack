package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.GroupProgress
import com.joaoeoneves.fintrack.domain.model.PlannedSource
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.icon
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * The "Groups" card: planned vs actual per group with a thin progress bar, each expandable to its categories.
 * [hasGroups] is whether any group is defined (`null` while loading: nothing shows); without one only the suggested
 * starter set and "Manage groups" are offered.
 */
@Composable
fun GroupsCard(
    groups: List<GroupProgress>,
    hasGroups: Boolean?,
    actions: GroupsActions,
    modifier: Modifier = Modifier,
) {
    // Groups are optional: until one is defined (or while loading) only a light entry point shows.
    if (hasGroups == null) return
    SettingsGroup(label = stringResource(R.string.plan_groups_title), modifier = modifier) {
        if (hasGroups) {
            groups.forEach { progress ->
                GroupProgressRow(progress)
                Hairline()
            }
        } else {
            GhostButton(text = stringResource(R.string.plan_groups_suggest), onClick = actions.onCreateSuggested)
        }
        GhostButton(
            text = stringResource(R.string.plan_groups_manage),
            onClick = actions.onManage,
            modifier = Modifier.padding(top = Dimens.Space12),
        )
    }
}

@Composable
private fun groupName(progress: GroupProgress) = progress.group?.name ?: stringResource(R.string.plan_group_other)

@Composable
private fun GroupProgressRow(progress: GroupProgress) {
    var expanded by rememberSaveable(progress.group?.id) { mutableStateOf(false) }
    val name = groupName(progress)
    val toggleDescription = stringResource(R.string.cd_group_toggle, name)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { contentDescription = toggleDescription }
                .padding(vertical = Dimens.Space12),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AmountsText(spent = progress.spent, planned = progress.planned, isOver = progress.isOver)
        }
        ThinProgressBar(
            progress = progress.fraction(),
            isOver = progress.isOver,
            modifier = Modifier.padding(top = Dimens.Space8),
        )
        if (progress.hasRecurringPlanned) FromRecurringHint()
        if (expanded) {
            progress.categories.forEach { CategoryProgressRow(it) }
        }
    }
}

@Composable
private fun AmountsText(
    spent: Long,
    planned: Long,
    isOver: Boolean,
) {
    Text(
        text = stringResource(R.string.budget_spent_of_limit, formatAmountCents(spent), formatAmountCents(planned)),
        style = Amount,
        color = if (isOver) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun FromRecurringHint() {
    Text(
        text = stringResource(R.string.plan_from_recurring),
        style = MaterialTheme.typography.bodySmall,
        color = FinTrackTheme.colors.textSecondary,
        modifier = Modifier.padding(top = Dimens.Space4),
    )
}

@Composable
private fun CategoryProgressRow(row: CategoryBudget) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight).padding(top = Dimens.Space8),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = row.category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.category.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (row.plannedSource == PlannedSource.RECURRING) {
                Text(
                    text = stringResource(R.string.plan_from_recurring),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
        }
        AmountsText(spent = row.spentCents, planned = row.planned ?: 0L, isOver = row.isOverBudget)
    }
}

/** How much of the group's planned amount is used, as a 0..1 bar fill: 0 without a planned amount. */
private fun GroupProgress.fraction(): Float {
    if (planned <= 0L) return 0f
    return (spent.toDouble() / planned.toDouble()).toFloat().coerceIn(0f, 1f)
}
