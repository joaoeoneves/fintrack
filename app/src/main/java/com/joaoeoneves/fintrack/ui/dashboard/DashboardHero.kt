package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.formatPercent
import com.joaoeoneves.fintrack.ui.designsystem.AnimatedAmountText
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.HeroAmount
import com.joaoeoneves.fintrack.ui.theme.SectionLabel
import java.util.Locale

/**
 * Formats a [savingsRate][com.joaoeoneves.fintrack.domain.model.savingsRate] fraction as a
 * whole-number percentage in [locale], e.g. 0.25 -> "25%" (en) / "25 %" (pt-PT).
 */
fun formatSavingsRate(
    rate: Double,
    locale: Locale,
): String = formatPercent(rate, locale)

/**
 * The dashboard's headline: the "Net Balance" label, the balance in the big hero figure and the
 * savings rate. While the chart is scrubbed, [scrubbedCents] replaces the balance with the value at
 * the finger, shown without the count-up animation so it tracks the drag.
 */
@Composable
fun DashboardHero(
    netCents: Long,
    savingsRate: Double?,
    modifier: Modifier = Modifier,
    scrubbedCents: Long? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dashboard_net_balance),
            style = SectionLabel,
            color = FinTrackTheme.colors.textSecondary,
        )
        HeroBalance(
            cents = scrubbedCents ?: netCents,
            scrubbing = scrubbedCents != null,
            modifier = Modifier.padding(top = Dimens.Space4),
        )
        SavingsRateText(savingsRate = savingsRate, modifier = Modifier.padding(top = Dimens.Space4))
    }
}

@Composable
private fun HeroBalance(
    cents: Long,
    scrubbing: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (cents < 0) FinTrackTheme.colors.negative else MaterialTheme.colorScheme.onBackground
    if (scrubbing) {
        Text(text = formatAmountCents(cents), style = HeroAmount, color = color, maxLines = 1, modifier = modifier)
    } else {
        AnimatedAmountText(cents = cents, style = HeroAmount, color = color, modifier = modifier)
    }
}

@Composable
private fun SavingsRateText(
    savingsRate: Double?,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val rateText =
        if (savingsRate != null) {
            remember(savingsRate, locale) { formatSavingsRate(savingsRate, locale) }
        } else {
            stringResource(R.string.dashboard_savings_rate_unavailable)
        }
    Text(
        text = stringResource(R.string.dashboard_savings_rate, rateText),
        style = MaterialTheme.typography.bodyMedium,
        color = FinTrackTheme.colors.textSecondary,
        textAlign = TextAlign.Start,
        modifier = modifier,
    )
}
