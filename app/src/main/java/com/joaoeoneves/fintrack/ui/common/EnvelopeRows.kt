package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.ui.plan.fundingLabel
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * The neutral note under a funded envelope: "Funded by X", or, when the envelope commits more than its funding
 * line brings in, how much X covers and how much comes from other income. Never drawn as an error.
 */
@Composable
fun FundedEnvelopeNote(
    lineLabel: String,
    coveredCents: Long,
    overflowCents: Long,
    modifier: Modifier = Modifier,
) {
    val text =
        if (overflowCents > 0L) {
            stringResource(
                R.string.envelope_funded_overflow,
                lineLabel,
                formatAmountCents(coveredCents),
                formatAmountCents(overflowCents),
            )
        } else {
            stringResource(R.string.envelope_card_funded, lineLabel)
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = FinTrackTheme.colors.textSecondary,
        modifier = modifier,
    )
}

/** One [FundedEnvelopeNote] per funded envelope that commits beyond its funding; nothing without overflow. */
@Composable
fun EnvelopeOverflowRow(
    summary: EnvelopesSummary,
    modifier: Modifier = Modifier,
) {
    val overflowing = summary.overflowing
    if (overflowing.isEmpty()) return
    Column(modifier = modifier) {
        overflowing.forEachIndexed { index, progress ->
            val line = progress.fundingLine ?: return@forEachIndexed
            FundedEnvelopeNote(
                lineLabel = line.fundingLabel(),
                coveredCents = progress.fundingCents ?: 0L,
                overflowCents = progress.overflowCents,
                modifier = if (index > 0) Modifier.padding(top = Dimens.Space4) else Modifier,
            )
        }
    }
}
