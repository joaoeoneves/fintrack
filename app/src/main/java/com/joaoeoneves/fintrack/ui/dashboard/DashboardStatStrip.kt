package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private const val MIN_AMOUNT_SP = 11
private const val MAX_AMOUNT_SP = 15

/** Income / Spending / Saved & invested side by side, separated by vertical hairlines. */
@Composable
fun DashboardStatStrip(
    incomeCents: Long,
    spendingCents: Long,
    savedCents: Long,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Stat(
            label = stringResource(R.string.dashboard_income_label),
            cents = incomeCents,
            modifier = Modifier.weight(1f).padding(end = Dimens.Space12),
        )
        VerticalHairline()
        Stat(
            label = stringResource(R.string.dashboard_expenses_label),
            cents = spendingCents,
            modifier = Modifier.weight(1f).padding(horizontal = Dimens.Space12),
        )
        VerticalHairline()
        Stat(
            label = stringResource(R.string.dashboard_saved_label),
            cents = savedCents,
            modifier = Modifier.weight(1f).padding(start = Dimens.Space12),
        )
    }
}

@Composable
private fun Stat(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
            maxLines = 2,
        )
        Text(
            text = formatAmountCents(cents),
            style = Amount,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_AMOUNT_SP.sp, maxFontSize = MAX_AMOUNT_SP.sp),
            modifier = Modifier.padding(top = Dimens.Space4),
        )
    }
}

@Composable
private fun VerticalHairline() {
    Box(
        modifier =
            Modifier
                .width(Dimens.HairlineThickness)
                .fillMaxHeight()
                .background(FinTrackTheme.colors.hairline),
    )
}
