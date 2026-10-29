package com.joaoeoneves.fintrack.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.monthlyEquivalentCents
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.rememberLocaleAwareDateFormatter
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The annualized expense of the active items, with its monthly equivalent. */
@Composable
internal fun RecurringTotals(state: RecurringListUiState.Content) {
    Column {
        SettingsRow(
            title =
                stringResource(
                    R.string.recurring_annual_cost,
                    formatAmountCents(state.annualExpenseCents),
                    formatAmountCents(monthlyEquivalentCents(state.annualExpenseCents)),
                ),
        )
        Hairline()
    }
}

@Composable
internal fun RecurringRowItem(
    row: RecurringRow,
    actions: RecurringListActions,
    modifier: Modifier = Modifier,
) {
    val item = row.item
    val amount = formatAmountCents(item.amountCents)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { actions.onOpenEditor(item.id) }
                .padding(vertical = Dimens.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
            val isMonthly = item.frequency == RecurringFrequency.MONTHLY
            val frequencyRes = if (isMonthly) R.string.recurring_per_month else R.string.recurring_per_year
            Text(
                text = stringResource(frequencyRes, amount),
                style = MaterialTheme.typography.bodyMedium,
                color = FinTrackTheme.colors.textSecondary,
            )
            row.category?.let {
                Text(
                    text = it.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
            if (item.autoPost) {
                Text(
                    text = stringResource(R.string.recurring_automatic),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
            Text(
                text = statusText(row),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
            )
        }
        val toggleLabel = stringResource(if (item.active) R.string.recurring_pause else R.string.recurring_resume)
        LabeledIconButton(
            icon = if (item.active) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = toggleLabel,
            onClick = { actions.onTogglePaused(item.id) },
        )
        LabeledIconButton(
            icon = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.cd_delete),
            onClick = { actions.onDeleteRequested(item.id) },
        )
    }
}

@Composable
private fun statusText(row: RecurringRow): String {
    val formatter = rememberLocaleAwareDateFormatter()
    return when {
        row.isEnded -> stringResource(R.string.recurring_ended)
        !row.item.active -> stringResource(R.string.recurring_paused)
        row.nextDue != null -> stringResource(R.string.recurring_next_due, formatter.format(row.nextDue))
        else -> ""
    }
}

/** Confirms deleting a recurring item; the transactions it already created are kept. */
@Composable
internal fun RecurringDeleteDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurring_delete_confirm, name)) },
        confirmButton = { DestructiveTextButton(text = stringResource(R.string.cd_delete), onClick = onConfirm) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
