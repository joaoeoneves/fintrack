package com.joaoeoneves.fintrack.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.UpcomingOccurrence
import com.joaoeoneves.fintrack.domain.usecase.RecurringOverview
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.rememberLocaleAwareDateFormatter
import java.time.LocalDate

/** How many upcoming rows the dashboard card shows. */
const val DASHBOARD_UPCOMING_LIMIT = 5

/** The card's state plus its callbacks, bundled to keep the dashboard's parameter lists short. */
data class UpcomingRecurringModel(
    val state: UpcomingRecurringUiState,
    val onConfirm: (UpcomingOccurrence) -> Unit,
    val onSkip: (UpcomingOccurrence) -> Unit,
    val onManage: () -> Unit,
)

/** Shows the upcoming-recurring view model's failure and "Added ..." events in [snackbarHostState]. */
@Composable
fun UpcomingRecurringSnackbars(
    viewModel: UpcomingRecurringViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }
    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect {
            snackbarHostState.showSnackbar(resources.getString(R.string.recurring_added, it))
        }
    }
}

/** The dashboard card listing what recurring bills/income are due; hidden while loading or on error. */
@Composable
fun UpcomingRecurringCard(
    model: UpcomingRecurringModel,
    modifier: Modifier = Modifier,
) {
    val state = model.state as? UpcomingRecurringUiState.Content ?: return
    Card(
        modifier = modifier.testTag(TestTags.RECURRING_CARD),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (state.overview.hasItems) {
            UpcomingContent(state = state, model = model)
        } else {
            Text(
                text = stringResource(R.string.recurring_empty_cta),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = model.onManage)
                        .padding(16.dp),
            )
        }
    }
}

@Composable
private fun UpcomingContent(
    state: UpcomingRecurringUiState.Content,
    model: UpcomingRecurringModel,
) {
    val rows = state.overview.upcoming
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.recurring_upcoming_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.recurring_nothing_due),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        rows.take(DASHBOARD_UPCOMING_LIMIT).forEach { row ->
            UpcomingRow(row = row, today = state.today, model = model)
            HorizontalDivider()
        }
        UpcomingFooter(overview = state.overview, onManage = model.onManage)
    }
}

@Composable
private fun UpcomingRow(
    row: UpcomingOccurrence,
    today: LocalDate,
    model: UpcomingRecurringModel,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = row.item.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(text = formatAmountCents(row.item.amountCents), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = occurrenceLabel(row.date, today),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (row.overdueCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.recurring_more_overdue, row.overdueCount, row.overdueCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row {
            TextButton(
                onClick = { model.onConfirm(row) },
                enabled = row.isDue,
                modifier = Modifier.testTag(TestTags.RECURRING_CONFIRM),
            ) { Text(stringResource(R.string.recurring_confirm)) }
            TextButton(
                onClick = { model.onSkip(row) },
                modifier = Modifier.testTag(TestTags.RECURRING_SKIP),
            ) { Text(stringResource(R.string.recurring_skip)) }
        }
    }
}

@Composable
private fun occurrenceLabel(
    date: LocalDate,
    today: LocalDate,
): String =
    when {
        date < today -> stringResource(R.string.recurring_overdue)
        date == today -> stringResource(R.string.recurring_due_today)
        else -> rememberLocaleAwareDateFormatter().format(date)
    }

@Composable
private fun UpcomingFooter(
    overview: RecurringOverview,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                if (overview.annualExpenseCents > 0) {
                    stringResource(
                        R.string.recurring_annual_cost,
                        formatAmountCents(overview.annualExpenseCents),
                        formatAmountCents(monthlyEquivalentCents(overview.annualExpenseCents)),
                    )
                } else {
                    ""
                },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onManage) { Text(stringResource(R.string.recurring_manage)) }
    }
}
