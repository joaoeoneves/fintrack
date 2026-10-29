package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.text
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.MonthFigures
import com.joaoeoneves.fintrack.ui.common.FundedEnvelopeNote
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.formatMonthLabel
import com.joaoeoneves.fintrack.ui.designsystem.EnvelopeRow
import com.joaoeoneves.fintrack.ui.designsystem.EnvelopeRowData
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.rememberAnimatedCents
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth

/** Where the rows of the month summary lead; a `null` target leaves the row inert. */
class MonthSummaryActions(
    val onOpenIncome: () -> Unit,
    val onOpenRecurring: () -> Unit,
    val onOpenEnvelopes: () -> Unit,
    val onOpenSavings: () -> Unit,
)

/**
 * The month as a subtraction that adds up: what was carried over from [previousMonth] (when there is something),
 * plus the income, minus recurring, envelopes and savings, equals what is available to spend. The same
 * [MonthFigures] feed Home and the plan, so a label is never shown with two numbers. Each row opens its section;
 * [extraIncomeCents] (yearly and one-off income not yet received) is noted under the income.
 */
@Composable
fun MonthSummarySection(
    figures: MonthFigures,
    previousMonth: YearMonth,
    actions: MonthSummaryActions,
    modifier: Modifier = Modifier,
    extraIncomeCents: Long = 0L,
) {
    val month = formatMonthLabel(previousMonth, currentLocale(), titlecase = false)
    Column(modifier = modifier.fillMaxWidth()) {
        if (figures.carryCents != 0L) {
            SummaryRow(stringResource(R.string.plan_summary_carry, month), figures.carryCents, signed = true)
            Hairline()
        }
        SummaryRow(
            label = stringResource(R.string.plan_tile_income),
            cents = figures.incomeCents,
            onClick = actions.onOpenIncome,
            note =
                stringResource(R.string.plan_summary_extras_note, formatAmountCents(extraIncomeCents))
                    .takeIf { extraIncomeCents > 0L },
        )
        Hairline()
        SummaryRow(
            label = stringResource(R.string.plan_tile_bills),
            cents = -figures.recurringCents,
            onClick = actions.onOpenRecurring,
            signed = true,
        )
        Hairline()
        SummaryRow(
            label = stringResource(R.string.plan_tile_envelopes),
            cents = -figures.envelopesCents,
            onClick = actions.onOpenEnvelopes,
            signed = true,
        )
        Hairline()
        SummaryRow(
            label = stringResource(R.string.plan_tile_saving),
            cents = -figures.savingCents,
            onClick = actions.onOpenSavings,
            signed = true,
        )
        Hairline()
        val available = figures.availableCents
        SummaryRow(
            label = stringResource(R.string.plan_tile_free),
            cents = available,
            emphasized = true,
            negative = available < 0L,
        )
    }
}

@Suppress("LongParameterList") // one independent display option each
@Composable
private fun SummaryRow(
    label: String,
    cents: Long,
    onClick: (() -> Unit)? = null,
    signed: Boolean = false,
    emphasized: Boolean = false,
    negative: Boolean = false,
    note: String? = null,
) {
    val base = Modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight)
    Row(
        modifier = (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            )
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
        }
        val prefix = if (signed && cents > 0L) "+" else ""
        Text(
            text = prefix + formatAmountCents(cents),
            style = Amount,
            color = if (negative) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Where tapping an envelope card leads, bundled for detekt's LongParameterList. */
class EnvelopeCardActions(
    val onOpenEnvelope: (String) -> Unit,
    val onOpenEnvelopes: () -> Unit,
    val onOpenCategories: () -> Unit,
)

/** The "Envelopes" section: one card per envelope (tap to edit), or the empty row that opens the envelopes list. */
@Composable
fun EnvelopeCardsSection(
    cards: List<EnvelopeCardUi>,
    actions: EnvelopeCardActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = Dimens.Space24)) {
        SectionTitle(title = stringResource(R.string.plan_tile_envelopes))
        if (cards.isEmpty()) {
            SettingsRow(
                title = stringResource(R.string.plan_envelopes_empty_cta),
                onClick = actions.onOpenEnvelopes,
                showChevron = true,
            )
        }
        cards.forEach { card ->
            // The "No envelope" card has nothing to open: it only lists what no envelope covers yet.
            EnvelopeCard(card = card, onClick = card.id?.let { id -> { actions.onOpenEnvelope(id) } })
            Hairline()
        }
    }
}

@Composable
private fun EnvelopeCard(
    card: EnvelopeCardUi,
    onClick: (() -> Unit)?,
) {
    val line = card.fundingLine
    EnvelopeRow(
        data = card.toRowData(),
        onClick = onClick,
        footer =
            line?.let {
                {
                    FundedEnvelopeNote(
                        lineLabel = it.fundingLabel(),
                        coveredCents = card.fundingCents,
                        overflowCents = card.overflowCents,
                        modifier = Modifier.padding(top = Dimens.Space4),
                    )
                }
            },
    )
}

@Composable
private fun EnvelopeCardUi.toRowData(): EnvelopeRowData {
    val name = name ?: stringResource(R.string.plan_group_other)
    return when (this) {
        is EnvelopeCardUi.Budgeted -> {
            val animatedSpent by rememberAnimatedCents(spentCents)
            val animatedBudget by rememberAnimatedCents(amountCents)
            val of = R.string.envelope_card_of
            EnvelopeRowData(
                name = name,
                amountText = stringResource(of, formatAmountCents(animatedSpent), formatAmountCents(animatedBudget)),
                amountSemantics = stringResource(of, formatAmountCents(spentCents), formatAmountCents(amountCents)),
                isOver = isOver,
                progress = fraction(),
                overText =
                    if (isOver) {
                        stringResource(R.string.envelope_card_over, formatAmountCents(-remainingCents))
                    } else {
                        null
                    },
            )
        }
        is EnvelopeCardUi.BillsOnly ->
            EnvelopeRowData(
                name = name,
                progress = fraction(),
                caption = billsCaption(),
            )
        is EnvelopeCardUi.Tracking ->
            EnvelopeRowData(
                name = name,
                caption = stringResource(R.string.envelope_card_spent, formatAmountCents(spentCents)),
            )
    }
}

private fun EnvelopeCardUi.Budgeted.fraction(): Float =
    if (amountCents > 0L) (spentCents.toDouble() / amountCents.toDouble()).toFloat() else 0f

private fun EnvelopeCardUi.BillsOnly.fraction(): Float =
    if (plannedCents > 0L) (paidCents.toDouble() / plannedCents.toDouble()).toFloat() else 0f

@Composable
private fun EnvelopeCardUi.BillsOnly.billsCaption(): String =
    stringResource(R.string.envelope_card_paid, formatAmountCents(paidCents), formatAmountCents(plannedCents))
