package com.joaoeoneves.fintrack.ui.recurring

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.monthlyEquivalentCents
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.icon
import com.joaoeoneves.fintrack.ui.common.rememberLocaleAwareDateFormatter
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.TransactionRow

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

/**
 * One recurring item as a transaction-style row: icon bubble, truncating name, a one-line subtitle (frequency,
 * category, automatic, status) and the amount; tap opens the editor (which also deletes). Pause/resume stays as the
 * single trailing action.
 */
@Composable
internal fun RecurringRowItem(
    row: RecurringRow,
    actions: RecurringListActions,
    modifier: Modifier = Modifier,
) {
    val item = row.item
    val amount = formatAmountCents(item.amountCents)
    val frequencyRes =
        if (item.frequency == RecurringFrequency.MONTHLY) R.string.recurring_per_month else R.string.recurring_per_year
    val subtitle =
        listOfNotNull(
            stringResource(frequencyRes, amount),
            row.category?.displayName,
            stringResource(R.string.recurring_automatic).takeIf { item.autoPost },
            statusText(row).takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TransactionRow(
            icon = row.category?.icon ?: Icons.Filled.Repeat,
            title = item.name,
            subtitle = subtitle,
            amountText = amount,
            amountColor = MaterialTheme.colorScheme.onSurface,
            onClick = { actions.onOpenEditor(item.id) },
            modifier = Modifier.weight(1f),
        )
        val toggleLabel = stringResource(if (item.active) R.string.recurring_pause else R.string.recurring_resume)
        LabeledIconButton(
            icon = if (item.active) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = toggleLabel,
            onClick = { actions.onTogglePaused(item.id) },
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
