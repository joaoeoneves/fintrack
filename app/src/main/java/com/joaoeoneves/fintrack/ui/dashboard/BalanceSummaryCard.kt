package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.ui.common.color
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import java.text.NumberFormat
import java.util.Locale

/**
 * Formats a [savingsRate][com.joaoeoneves.fintrack.domain.model.savingsRate] fraction as a
 * whole-number percentage in [locale], e.g. 0.25 -> "25%" (en) / "25 %" (pt-PT).
 */
fun formatSavingsRate(
    rate: Double,
    locale: Locale,
): String = percentFormatter(locale).format(rate)

private fun percentFormatter(locale: Locale): NumberFormat =
    NumberFormat.getPercentInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

/**
 * The dashboard's headline card: the period's net balance, an Income / Spending / Saved & invested
 * breakdown and the savings rate.
 */
@Composable
fun BalanceSummaryCard(
    state: DashboardUiState.Content,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.dashboard_net_balance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NetBalanceAmountText(netCents = state.netCents)

            HorizontalDivider()

            BalanceStatsRow(state = state, modifier = Modifier.padding(top = 16.dp))

            SavingsRateText(savingsRate = state.savingsRate, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun NetBalanceAmountText(netCents: Long) {
    Text(
        text = formatAmountCents(netCents),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = if (netCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 32.sp),
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
    )
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun BalanceStatsRow(
    state: DashboardUiState.Content,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        BalanceSummaryColumn(
            stat =
                BalanceStat(
                    label = stringResource(R.string.dashboard_income_label),
                    amountCents = state.incomeCents,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    tint = MaterialTheme.colorScheme.primary,
                    amountColor = MaterialTheme.colorScheme.primary,
                ),
            modifier = Modifier.weight(1f),
        )
        BalanceSummaryColumn(
            stat =
                BalanceStat(
                    label = stringResource(R.string.dashboard_expenses_label),
                    amountCents = state.spendingCents,
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    tint = MaterialTheme.colorScheme.error,
                    amountColor = MaterialTheme.colorScheme.onSurface,
                ),
            modifier = Modifier.weight(1f),
        )
        BalanceSummaryColumn(
            stat =
                BalanceStat(
                    label = stringResource(R.string.dashboard_saved_label),
                    amountCents = state.savedCents,
                    icon = Icons.Default.Savings,
                    tint = ExpenseCategory.INVESTMENTS.color,
                    amountColor = MaterialTheme.colorScheme.onSurface,
                ),
            modifier = Modifier.weight(1f),
        )
    }
}

/** A single income/spending/saved stat shown by [BalanceSummaryColumn], bundled to keep its parameter list short. */
private data class BalanceStat(
    val label: String,
    val amountCents: Long,
    val icon: ImageVector,
    val tint: Color,
    val amountColor: Color,
)

@Composable
private fun BalanceSummaryColumn(
    stat: BalanceStat,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(stat.tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = stat.icon, contentDescription = null, tint = stat.tint)
        }
        Text(
            text = stat.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        // Three columns share the card's width, so amounts shrink to fit on one line instead of
        // wrapping mid-number.
        Text(
            text = formatAmountCents(stat.amountCents),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = stat.amountColor,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 16.sp),
        )
    }
}
