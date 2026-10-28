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
import com.joaoeoneves.fintrack.domain.model.AnnualSubscription
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
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

/** Planned vs year-to-date actual per envelope, with a thin bar; envelopes with nothing at all are left out. */
@Composable
fun AnnualEnvelopesCard(
    envelopes: List<AnnualEnvelope>,
    modifier: Modifier = Modifier,
) {
    val shown = envelopes.filter { it.plannedCents > 0 || it.actualCents > 0 }
    if (shown.isEmpty()) return
    SettingsGroup(label = stringResource(R.string.plan_envelopes_title), modifier = modifier) {
        shown.forEach { group ->
            AnnualEnvelopeRow(group)
            Hairline()
        }
    }
}

@Composable
private fun AnnualEnvelopeRow(group: AnnualEnvelope) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space12)) {
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
                        R.string.annual_actual_of_planned,
                        formatAmountCents(group.actualCents),
                        formatAmountCents(group.plannedCents),
                    ),
                style = Amount,
                color = if (group.isOver) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onSurface,
            )
        }
        ThinProgressBar(
            progress = group.fraction(),
            isOver = group.isOver,
            modifier = Modifier.padding(top = Dimens.Space8),
        )
    }
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
