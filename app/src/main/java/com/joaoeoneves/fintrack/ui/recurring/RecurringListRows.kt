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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.rememberLocaleAwareDateFormatter

/** The annualized expense (with its monthly equivalent) and income of the active items. */
@Composable
internal fun RecurringTotals(state: RecurringListUiState.Content) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text =
                stringResource(
                    R.string.recurring_annual_cost,
                    formatAmountCents(state.annualExpenseCents),
                    formatAmountCents(monthlyEquivalentCents(state.annualExpenseCents)),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.recurring_annual_income, formatAmountCents(state.annualIncomeCents)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun RecurringRowItem(
    row: RecurringRow,
    actions: RecurringListActions,
) {
    val item = row.item
    val amount = formatAmountCents(item.amountCents)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { actions.onOpenEditor(item.id) }
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
            val isMonthly = item.frequency == RecurringFrequency.MONTHLY
            val frequencyRes = if (isMonthly) R.string.recurring_per_month else R.string.recurring_per_year
            Text(
                text = stringResource(frequencyRes, amount),
                style = MaterialTheme.typography.bodyMedium,
            )
            row.category?.let { Text(text = it.displayName, style = MaterialTheme.typography.bodySmall) }
            Text(
                text = statusText(row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurring_delete_confirm, name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.cd_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
