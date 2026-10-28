package com.joaoeoneves.fintrack.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.ExpectedIncome
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.UpcomingOccurrence
import com.joaoeoneves.fintrack.domain.model.amountIn
import com.joaoeoneves.fintrack.domain.model.payDateIn
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.rememberLocaleAwareDateFormatter
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SectionAction
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.pressScale
import com.joaoeoneves.fintrack.ui.plan.LocalReceiveFeedback
import com.joaoeoneves.fintrack.ui.plan.ReceiveChange
import com.joaoeoneves.fintrack.ui.plan.ReceivedCheckButton
import com.joaoeoneves.fintrack.ui.plan.displayLabel
import com.joaoeoneves.fintrack.ui.plan.rememberPaydayText
import com.joaoeoneves.fintrack.ui.plan.rememberReceivedText
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.LocalDate

/** How many upcoming rows the dashboard card shows. */
const val DASHBOARD_UPCOMING_LIMIT = 5

/** The card's state plus its callbacks, bundled to keep the dashboard's parameter lists short. */
data class UpcomingRecurringModel(
    val state: UpcomingRecurringUiState,
    val onConfirm: (UpcomingOccurrence) -> Unit,
    val onSkip: (UpcomingOccurrence) -> Unit,
    val onManage: () -> Unit,
    val onReceiveIncome: (row: IncomeRow, date: LocalDate, amountCents: Long?) -> Unit,
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

/**
 * The dashboard block listing what recurring bills/income are due; hidden while loading or on
 * error. Without any recurring items it collapses to a single call-to-action row.
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
        state.expectedIncome?.takeIf { it.rows.isNotEmpty() }?.let {
            ExpectedIncomeGroup(expected = it, today = state.today, onReceive = model.onReceiveIncome)
        }
    }
}

/**
 * The current month's income not yet received, offered from a week before payday: each row with a check that
 * records it as received today and offers to change the date or amount from the snackbar.
 */
@Composable
private fun ExpectedIncomeGroup(
    expected: ExpectedIncome,
    today: LocalDate,
    onReceive: (row: IncomeRow, date: LocalDate, amountCents: Long?) -> Unit,
) {
    val feedback = LocalReceiveFeedback.current
    val changeLabel = stringResource(R.string.action_change)
    SectionTitle(title = stringResource(R.string.plan_expected_income))
    expected.rows.forEachIndexed { index, row ->
        if (index > 0) Hairline()
        val title = row.displayLabel()
        val payDate = row.payDateIn(expected.month)
        val receiveDate = minOf(payDate, today)
        val snackbarText = stringResource(R.string.plan_received_on, rememberReceivedText(receiveDate))
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space4)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(text = formatAmountCents(row.cents), style = Amount, maxLines = 1)
                if (row.cents > 0L) {
                    ReceivedCheckButton(
                        received = false,
                        onClick = {
                            onReceive(row, receiveDate, null)
                            val paying = row.lines.filter { it.amountIn(expected.month, expected.workingDays) > 0L }
                            feedback?.announce(
                                message = snackbarText,
                                actionLabel = changeLabel,
                                change =
                                    ReceiveChange(
                                        title = title,
                                        today = today,
                                        date = receiveDate,
                                        editableAmountCents =
                                            paying.singleOrNull()?.amountIn(expected.month, expected.workingDays),
                                        onConfirm = { date, cents -> onReceive(row, date, cents) },
                                    ),
                            )
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.plan_payday, rememberPaydayText(payDate)),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = Dimens.Space8),
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
        UpcomingRow(row = row, today = state.today, model = model)
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

@Composable
private fun UpcomingRow(
    row: UpcomingOccurrence,
    today: LocalDate,
    model: UpcomingRecurringModel,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space12)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space12)) {
            Text(
                text = row.item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = formatAmountCents(row.item.amountCents), style = Amount, maxLines = 1)
        }
        Text(
            text = occurrenceLabel(row.date, today),
            style = MaterialTheme.typography.bodySmall,
            color = if (row.isDue) FinTrackTheme.colors.accentText else FinTrackTheme.colors.textSecondary,
        )
        if (row.overdueCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.recurring_more_overdue, row.overdueCount, row.overdueCount),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.negative,
            )
        }
        Row(
            modifier = Modifier.padding(top = Dimens.Space12),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            CompactPill(
                text = stringResource(R.string.recurring_confirm),
                onClick = { model.onConfirm(row) },
                enabled = row.isDue,
                modifier = Modifier.testTag(TestTags.RECURRING_CONFIRM),
            )
            CompactPill(
                text = stringResource(R.string.recurring_skip),
                onClick = { model.onSkip(row) },
                modifier = Modifier.testTag(TestTags.RECURRING_SKIP),
            )
        }
    }
}

/** A small surface-toned pill button, sized for in-row actions. */
@Composable
private fun CompactPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = Dimens.Space16, vertical = 0.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = FinTrackTheme.colors.surface2,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = FinTrackTheme.colors.surface2,
                disabledContentColor = FinTrackTheme.colors.textSecondary.copy(alpha = DISABLED_ALPHA),
            ),
        modifier = modifier.height(PillHeight).pressScale(interactionSource),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

private val PillHeight = 36.dp
private const val DISABLED_ALPHA = 0.7f

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
