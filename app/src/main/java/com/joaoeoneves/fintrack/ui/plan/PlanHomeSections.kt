package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.FundedEnvelopeNote
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
import com.joaoeoneves.fintrack.ui.designsystem.pressScale
import com.joaoeoneves.fintrack.ui.designsystem.rememberAnimatedCents
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The five figures of the selected month as a two-column grid of tiles; "Free" is red when negative. */
@Composable
fun PlanTilesRow(
    tiles: PlanTiles,
    modifier: Modifier = Modifier,
) {
    val freeLabel = stringResource(R.string.plan_tile_free)
    val items =
        listOf(
            stringResource(R.string.plan_tile_income) to tiles.incomeCents,
            stringResource(R.string.plan_tile_bills) to tiles.billsCents,
            stringResource(R.string.plan_tile_saving) to tiles.savingCents,
            stringResource(R.string.plan_tile_envelopes) to tiles.envelopesCents,
            freeLabel to tiles.freeCents,
        )
    Column(
        modifier = modifier.fillMaxWidth().padding(top = Dimens.Space16),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
    ) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space16)) {
                pair.forEach { (label, cents) ->
                    PlanTile(
                        label = label,
                        cents = cents,
                        negative = label == freeLabel && cents < 0L,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Column(modifier = Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun PlanTile(
    label: String,
    cents: Long,
    negative: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
        )
        Text(
            text = formatAmountCents(cents),
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
            EnvelopeCard(
                card = card,
                onClick = { card.id?.let(actions.onOpenEnvelope) ?: actions.onOpenCategories() },
            )
            Hairline()
        }
    }
}

@Composable
private fun EnvelopeCard(
    card: EnvelopeCardUi,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(vertical = Dimens.Space12),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = card.name ?: stringResource(R.string.plan_group_other),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val headline = card.headline()
            if (headline != null) {
                Text(
                    text = headline.shown,
                    style = Amount,
                    color =
                        if (card is EnvelopeCardUi.Budgeted && card.isOver) {
                            FinTrackTheme.colors.negative
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(headline.final) },
                )
            }
        }
        CardProgress(card)
        CardCaptions(card)
    }
}

/** Shown (counting) and final text of an envelope card headline. */
private data class Headline(
    val shown: String,
    val final: String,
)

@Composable
private fun EnvelopeCardUi.headline(): Headline? =
    when (this) {
        is EnvelopeCardUi.Budgeted -> {
            val spent by rememberAnimatedCents(spentCents)
            val budget by rememberAnimatedCents(amountCents)
            Headline(
                shown = stringResource(R.string.envelope_card_of, formatAmountCents(spent), formatAmountCents(budget)),
                final =
                    stringResource(
                        R.string.envelope_card_of,
                        formatAmountCents(spentCents),
                        formatAmountCents(amountCents),
                    ),
            )
        }
        is EnvelopeCardUi.BillsOnly -> {
            val paid by rememberAnimatedCents(paidCents)
            val planned by rememberAnimatedCents(plannedCents)
            Headline(
                shown =
                    stringResource(R.string.envelope_card_paid, formatAmountCents(paid), formatAmountCents(planned)),
                final =
                    stringResource(
                        R.string.envelope_card_paid,
                        formatAmountCents(paidCents),
                        formatAmountCents(plannedCents),
                    ),
            )
        }
        is EnvelopeCardUi.Tracking -> null
    }

@Composable
private fun CardProgress(card: EnvelopeCardUi) {
    val (spent, total, over) =
        when (card) {
            is EnvelopeCardUi.Budgeted -> Triple(card.spentCents, card.amountCents, card.isOver)
            is EnvelopeCardUi.BillsOnly -> Triple(card.paidCents, card.plannedCents, false)
            is EnvelopeCardUi.Tracking -> return
        }
    val fraction = if (total > 0L) (spent.toDouble() / total.toDouble()).toFloat() else 0f
    ThinProgressBar(progress = fraction, isOver = over, modifier = Modifier.padding(top = Dimens.Space8))
}

@Composable
private fun CardCaptions(card: EnvelopeCardUi) {
    val secondary = FinTrackTheme.colors.textSecondary
    val style = MaterialTheme.typography.bodySmall
    when (card) {
        is EnvelopeCardUi.Budgeted ->
            if (card.isOver) {
                val over by rememberAnimatedCents(-card.remainingCents)
                val finalText = stringResource(R.string.envelope_card_over, formatAmountCents(-card.remainingCents))
                Text(
                    text = stringResource(R.string.envelope_card_over, formatAmountCents(over)),
                    style = style,
                    color = FinTrackTheme.colors.negative,
                    modifier =
                        Modifier
                            .padding(top = Dimens.Space4)
                            .clearAndSetSemantics { text = AnnotatedString(finalText) },
                )
            }
        is EnvelopeCardUi.BillsOnly -> Unit
        is EnvelopeCardUi.Tracking ->
            Text(
                text = stringResource(R.string.envelope_card_spent, formatAmountCents(card.spentCents)),
                style = style,
                color = secondary,
            )
    }
    val line = card.fundingLine ?: return
    FundedEnvelopeNote(
        lineLabel = line.fundingLabel(),
        coveredCents = card.fundingCents,
        overflowCents = card.overflowCents,
        modifier = Modifier.padding(top = Dimens.Space4),
    )
}
