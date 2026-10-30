package com.joaoeoneves.fintrack.ui.activity.analytics

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.SpendingComparison
import com.joaoeoneves.fintrack.domain.model.percentChange
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.formatPercent
import com.joaoeoneves.fintrack.ui.common.period.formatShortMonthName
import com.joaoeoneves.fintrack.ui.designsystem.MonthlyBar
import com.joaoeoneves.fintrack.ui.designsystem.MonthlyBars
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val MIN_AVERAGE_MONTHS = 2

/** "+12 %" / "−5 %" (spending up reads as bad, down as good); zero is plain. */
private fun signedPercent(
    percent: Int,
    locale: Locale,
): String {
    val sign =
        when {
            percent > 0 -> "+"
            percent < 0 -> "−"
            else -> ""
        }
    return sign + formatPercent(kotlin.math.abs(percent), locale)
}

/** The lines under "Spent" for a single month: "vs previous month" and "vs 6-month average"; parts hide when null. */
@Composable
fun SpendingComparisonLines(
    comparison: SpendingComparison,
    modifier: Modifier = Modifier,
) {
    val vsPrevious = percentChange(comparison.currentCents, comparison.previousCents)
    val vsAverage =
        comparison.averageMonths
            .takeIf { it >= MIN_AVERAGE_MONTHS }
            ?.let { percentChange(comparison.currentCents, comparison.averageCents) }
    Column(modifier = modifier) {
        if (vsPrevious != null) {
            ChangeLine(stringResource(R.string.activity_vs_previous_month), vsPrevious)
        }
        if (vsAverage != null) {
            ChangeLine(stringResource(R.string.activity_vs_average, comparison.averageMonths), vsAverage)
        }
    }
}

@Composable
internal fun ChangeLine(
    label: String,
    percent: Int,
) {
    val color =
        when {
            percent > 0 -> FinTrackTheme.colors.negative
            percent < 0 -> FinTrackTheme.colors.positive
            else -> FinTrackTheme.colors.textSecondary
        }
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = FinTrackTheme.colors.textSecondary)
        Text(
            text = signedPercent(percent, currentLocale()),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

/** "Monthly average X" for a multi-month period. */
@Composable
fun MonthlyAverageLine(
    cents: Long,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.activity_monthly_average, formatAmountCents(cents)),
        style = MaterialTheme.typography.bodySmall,
        color = FinTrackTheme.colors.textSecondary,
        modifier = modifier,
    )
}

/**
 * How a Year chart words its bars' captions: [labelRes] is the caption of a bar ("Spent %1$s"); with [expectedByMonth]
 * a month that still expects income reads "Received X · Expected Y" ([receivedLabelRes] / [receivedExpectedLabelRes]).
 */
data class EvolutionCaptions(
    @param:StringRes val labelRes: Int,
    @param:StringRes val expectedLabelRes: Int? = null,
    val expectedByMonth: Map<YearMonth, Long> = emptyMap(),
)

/**
 * The Evolution bars; the period's months are highlighted and a tap shows a bar's caption (never filters). With
 * [onMonthClick] a tap also opens that bar's month.
 */
@Composable
fun EvolutionSection(
    evolution: Evolution,
    modifier: Modifier = Modifier,
    captions: EvolutionCaptions? = null,
    onMonthClick: ((YearMonth) -> Unit)? = null,
) {
    val locale = currentLocale()
    val long = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    val resources = LocalContext.current.resources
    val spoken = { month: String, amount: String ->
        resources.getString(R.string.cd_activity_evolution_bar, month, amount)
    }
    val bars =
        evolution.months.mapIndexed { i, month ->
            val value = evolution.valuesCents[i]
            MonthlyBar(
                label = formatShortMonthName(month, locale),
                valueCents = value,
                accessibilityText = spoken(month.format(long), formatAmountCents(value, false)),
                caption = captions?.let { captionOf(resources, it, month, value) },
            )
        }
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title = stringResource(R.string.activity_evolution))
        MonthlyBars(
            bars = bars,
            highlighted = evolution.highlighted,
            modifier = Modifier.padding(top = Dimens.Space8),
            onBarClick = onMonthClick?.let { open -> { index -> evolution.months.getOrNull(index)?.let(open) } },
        )
    }
}

@Composable
private fun captionOf(
    resources: android.content.res.Resources,
    captions: EvolutionCaptions,
    month: YearMonth,
    valueCents: Long,
): String {
    val value = formatAmountCents(valueCents)
    val expected = captions.expectedByMonth[month]?.takeIf { it > 0L }
    return if (expected != null && captions.expectedLabelRes != null) {
        resources.getString(captions.expectedLabelRes, value, formatAmountCents(expected))
    } else {
        resources.getString(captions.labelRes, value)
    }
}

/** The month's name for the "Counts for" badge, in the app's language. */
fun monthName(
    month: java.time.YearMonth,
    locale: Locale,
): String = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
