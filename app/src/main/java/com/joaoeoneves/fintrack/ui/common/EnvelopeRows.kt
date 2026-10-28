package com.joaoeoneves.fintrack.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.EnvelopesSummary
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** "€X above envelope Food": what the funded envelopes commit beyond their funding; nothing without overflow. */
@Composable
fun EnvelopeOverflowRow(
    summary: EnvelopesSummary,
    modifier: Modifier = Modifier,
) {
    val overflowing = summary.overflowing
    val first = overflowing.firstOrNull() ?: return
    Text(
        text =
            stringResource(
                R.string.plan_envelope_overflow,
                formatAmountCents(overflowing.sumOf { it.overflowCents }),
                first.envelope.name,
            ),
        style = MaterialTheme.typography.bodySmall,
        color = FinTrackTheme.colors.negative,
        modifier = modifier,
    )
}
