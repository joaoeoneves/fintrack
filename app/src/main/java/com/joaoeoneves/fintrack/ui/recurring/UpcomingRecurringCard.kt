package com.joaoeoneves.fintrack.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.ExpectedIncome
import com.joaoeoneves.fintrack.domain.model.UpcomingOccurrence
import com.joaoeoneves.fintrack.domain.model.monthlyEquivalentCents
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.rememberDayMonthText
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SectionAction
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.plan.displayLabel
import com.joaoeoneves.fintrack.ui.plan.rememberPaydayText
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.LocalDate

/** How many upcoming rows the dashboard card shows. */
const val DASHBOARD_UPCOMING_LIMIT = 5

/** The card's state plus its callbacks, bundled to keep the dashboard's parameter lists short. */
data class UpcomingRecurringModel(
    val state: UpcomingRecurringUiState,
    val onManage: () -> Unit,
)

/**
 * The dashboard block listing what recurring bills/income are coming up, read-only (bills post themselves and land in
 * "To review"); hidden while loading or on error. Without any recurring items it collapses to a single
 * call-to-action row.
 */
@Composable
fun UpcomingRecurringCard(
    model: UpcomingRecurringModel,
    modifier: Modifier = Modifier,
) {
    val state = model.state as? UpcomingRecurringUiState.Content ?: return
    Column(modifier = modifier.fillMaxWidth().testTag(TestTags.RECURRING_CARD)) {
        if (state.overview.hasItems) {
            UpcomingContent(state = state, model = model)
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = model.onManage)
                        .heightIn(min = Dimens.SettingsRowMinHeight),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.recurring_empty_cta),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = FinTrackTheme.colors.textSecondary,
                )
            }
            Hairline()
        }
        state.expectedIncome?.takeIf { it.entries.isNotEmpty() }?.let { ExpectedIncomeGroup(expected = it) }
    }
}

/** The current month's income still to come, from a week before payday: name, pay date and amount, nothing to tap. */
@Composable
private fun ExpectedIncomeGroup(expected: ExpectedIncome) {
    SectionTitle(title = stringResource(R.string.home_to_receive_title))
    expected.entries.forEachIndexed { index, entry ->
        if (index > 0) Hairline()
        val row = entry.row
        key(row.lines.first().id, entry.payDate) {
            SettingsRow(
                title = row.displayLabel(),
                supportingText = stringResource(R.string.plan_payday, rememberPaydayText(entry.payDate)),
                value = formatAmountCents(row.cents),
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
    SectionTitle(
        title = stringResource(R.string.recurring_upcoming_title),
        trailing = { SectionAction(text = stringResource(R.string.recurring_manage), onClick = model.onManage) },
    )
    if (rows.isEmpty()) {
        Text(
            text = stringResource(R.string.recurring_nothing_due),
            style = MaterialTheme.typography.bodyMedium,
            color = FinTrackTheme.colors.textSecondary,
            modifier = Modifier.padding(vertical = Dimens.Space8),
        )
    }
    rows.take(DASHBOARD_UPCOMING_LIMIT).forEachIndexed { index, row ->
        if (index > 0) Hairline()
        key(row.item.id, row.date) { UpcomingRow(row = row, today = state.today) }
    }
    if (state.overview.annualExpenseCents > 0) {
        Text(
            text =
                stringResource(
                    R.string.recurring_annual_cost,
                    formatAmountCents(state.overview.annualExpenseCents),
                    formatAmountCents(monthlyEquivalentCents(state.overview.annualExpenseCents)),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Dimens.Space12),
        )
    }
}

/** One upcoming bill: name, its date (or "overdue" / "due today") and amount. */
@Composable
private fun UpcomingRow(
    row: UpcomingOccurrence,
    today: LocalDate,
) {
    val status =
        when {
            row.date < today -> stringResource(R.string.recurring_overdue)
            row.date == today -> stringResource(R.string.recurring_due_today)
            else -> rememberDayMonthText(row.date)
        }
    val more =
        if (row.overdueCount > 0) {
            pluralStringResource(R.plurals.recurring_more_overdue, row.overdueCount, row.overdueCount)
        } else {
            null
        }
    SettingsRow(
        title = row.item.name,
        supportingText = listOfNotNull(status, more).joinToString(" · "),
        value = formatAmountCents(row.item.amountCents),
    )
}
