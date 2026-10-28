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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.domain.model.PlannedSource
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.icon
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * The "Limits" card once groups exist: per group a header (spent of planned, thin progress bar) with its category
 * rows under it, each opening the limit editor; ends with the "Manage groups" row.
 */
@Composable
fun LimitsByGroupCard(
    groups: List<LimitGroup>,
    onEdit: (CategoryBudget) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.plan_allocations), modifier = modifier) {
        groups.forEach { group ->
            LimitGroupHeader(group)
            group.rows.forEach { LimitCategoryRow(it, onClick = { onEdit(it) }) }
            Hairline()
        }
        SettingsRow(title = stringResource(R.string.plan_envelopes_manage), onClick = onManage, showChevron = true)
    }
}

@Composable
private fun LimitGroupHeader(group: LimitGroup) {
    val isOver = group.plannedCents > 0 && group.spentCents > group.plannedCents
    Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.Space12)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.envelope?.name ?: stringResource(R.string.plan_group_other),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.budget_spent_of_limit,
                        formatAmountCents(group.spentCents),
                        formatAmountCents(group.plannedCents),
                    ),
                style = Amount,
                color = if (isOver) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onSurface,
            )
        }
        ThinProgressBar(
            progress = group.fraction(),
            isOver = isOver,
            modifier = Modifier.padding(top = Dimens.Space8),
        )
    }
}

@Composable
private fun LimitCategoryRow(
    row: CategoryBudget,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.SettingsRowMinHeight)
                .clickable(onClick = onClick)
                .padding(top = Dimens.Space8),
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
        val planned = row.planned
        if (planned != null) {
            Text(
                text =
                    stringResource(
                        R.string.budget_spent_of_limit,
                        formatAmountCents(row.spentCents),
                        formatAmountCents(planned),
                    ),
                style = Amount,
                color = if (row.isOverBudget) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = stringResource(R.string.plan_not_planned),
                style = Amount.copy(fontWeight = FontWeight.Normal),
                color = FinTrackTheme.colors.textSecondary,
            )
        }
    }
}

/** How much of the group's planned amount is used, as a 0..1 bar fill: 0 without a planned amount. */
private fun LimitGroup.fraction(): Float {
    if (plannedCents <= 0L) return 0f
    return (spentCents.toDouble() / plannedCents.toDouble()).toFloat().coerceIn(0f, 1f)
}
