package com.joaoeoneves.fintrack.ui.plan.annual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AnnualEnvelope
import com.joaoeoneves.fintrack.domain.model.AnnualFunding
import com.joaoeoneves.fintrack.domain.model.AnnualIncome
import com.joaoeoneves.fintrack.domain.model.AnnualSavings
import com.joaoeoneves.fintrack.domain.model.AnnualSubscription
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.EnvelopeRow
import com.joaoeoneves.fintrack.ui.designsystem.EnvelopeRowData
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.plan.displayLabel
import com.joaoeoneves.fintrack.ui.plan.fundingLabel
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth

private const val MONTHS_PER_YEAR = 12

@Composable
private fun LabelAmountRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = Amount, color = valueColor)
    }
}

/** One row per income component or manual line with the months it pays when not every month, and the total. */
@Composable
fun AnnualIncomeCard(
    income: AnnualIncome,
    year: Int,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    SettingsGroup(label = stringResource(R.string.annual_income_title), modifier = modifier) {
        income.rows.forEach { row ->
            Column {
                LabelAmountRow(row.displayLabel(), formatAmountCents(row.cents))
                if (row.months.size < MONTHS_PER_YEAR) {
                    Text(
                        text = row.months.joinToString { formatShortMonthName(YearMonth.of(year, it), locale) },
                        style = MaterialTheme.typography.bodySmall,
                        color = FinTrackTheme.colors.textSecondary,
                        modifier = Modifier.padding(bottom = Dimens.Space8),
                    )
                }
            }
            Hairline()
        }
        LabelAmountRow(stringResource(R.string.annual_income_total), formatAmountCents(income.totalCents))
    }
}

/**
 * Planned vs year-to-date actual per envelope, as the same row as the month (tap an envelope to edit it);
 * envelopes with nothing at all are left out, and one without a limit shows what was spent, never a zero limit.
 */
@Composable
fun AnnualEnvelopesCard(
    envelopes: List<AnnualEnvelope>,
    modifier: Modifier = Modifier,
    savings: AnnualSavings = AnnualSavings(0L, 0L),
    onOpenEnvelope: (String) -> Unit = {},
) {
    val shown = envelopes.filter { it.plannedCents > 0 || it.actualCents > 0 }
    val hasSavings = savings.plannedCents > 0 || savings.actualCents > 0
    if (shown.isEmpty() && !hasSavings) return
    SettingsGroup(label = stringResource(R.string.plan_envelopes_title), modifier = modifier) {
        shown.forEach { group ->
            val id = group.envelope?.id
            AnnualEnvelopeRow(
                group = group,
                name = group.envelope?.name ?: stringResource(R.string.plan_group_other),
                onClick = id?.let { { onOpenEnvelope(it) } },
            )
            Hairline()
        }
        // Savings read the same as in the month: one "Savings" line, never an envelope.
        if (hasSavings) {
            AnnualEnvelopeRow(
                group = AnnualEnvelope(null, savings.plannedCents, savings.actualCents),
                name = stringResource(R.string.plan_tile_saving),
                onClick = null,
            )
            Hairline()
        }
    }
}

@Composable
private fun AnnualEnvelopeRow(
    group: AnnualEnvelope,
    name: String,
    onClick: (() -> Unit)?,
) {
    val data =
        if (group.plannedCents > 0L) {
            val actual = formatAmountCents(group.actualCents)
            EnvelopeRowData(
                name = name,
                amountText =
                    stringResource(R.string.annual_actual_of_planned, actual, formatAmountCents(group.plannedCents)),
                isOver = group.isOver,
                progress = group.fraction(),
                overText =
                    if (group.isOver) {
                        val over = formatAmountCents(group.actualCents - group.plannedCents)
                        stringResource(R.string.envelope_card_over, over)
                    } else {
                        null
                    },
            )
        } else {
            EnvelopeRowData(
                name = name,
                caption = stringResource(R.string.annual_envelope_spent, formatAmountCents(group.actualCents)),
            )
        }
    EnvelopeRow(data = data, onClick = onClick)
}

private fun AnnualEnvelope.fraction(): Float {
    if (plannedCents <= 0L) return 0f
    return (actualCents.toDouble() / plannedCents.toDouble()).toFloat().coerceIn(0f, 1f)
}

/** One line per funded envelope: what is left of its funding line's year income after what it plans. */
@Composable
fun AnnualFundingCard(
    funding: List<AnnualFunding>,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.annual_funding_title), modifier = modifier) {
        funding.forEachIndexed { index, pot ->
            if (index > 0) Hairline()
            AnnualFundingRow(pot)
        }
    }
}

@Composable
private fun AnnualFundingRow(pot: AnnualFunding) {
    val name = pot.envelope.name
    val left = pot.leftoverCents
    val line = pot.line.fundingLabel()
    val text =
        if (left >= 0) {
            stringResource(R.string.plan_pot_left, name, line, formatAmountCents(left))
        } else {
            stringResource(
                R.string.annual_funding_overflow,
                name,
                line,
                formatAmountCents(pot.incomeCents),
                formatAmountCents(-left),
            )
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = FinTrackTheme.colors.textSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.SettingsRowMinHeight)
                .padding(vertical = Dimens.Space8),
    )
}

/** The yearly recurring expenses with the month each is due and its amount. */
@Composable
fun AnnualSubscriptionsCard(
    subscriptions: List<AnnualSubscription>,
    year: Int,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    SettingsGroup(label = stringResource(R.string.annual_subscriptions_title), modifier = modifier) {
        subscriptions.forEachIndexed { index, subscription ->
            if (index > 0) Hairline()
            val month = formatShortMonthName(YearMonth.of(year, subscription.month), locale)
            LabelAmountRow(
                label = stringResource(R.string.annual_subscription_row, subscription.item.name, month),
                value = formatAmountCents(subscription.item.amountCents),
            )
        }
    }
}
