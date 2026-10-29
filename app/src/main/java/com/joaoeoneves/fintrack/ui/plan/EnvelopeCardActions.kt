package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.FundedEnvelopeNote
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.EnvelopeRow
import com.joaoeoneves.fintrack.ui.designsystem.EnvelopeRowData
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.rememberAnimatedCents
import com.joaoeoneves.fintrack.ui.theme.Dimens

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
    Column(modifier = modifier.fillMaxWidth().padding(top = Dimens.SectionGap)) {
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
