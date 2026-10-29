package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.IncomeSourceKey

/** Income per month (aligned to [MonthlyHistory.months]): everything funding the month, or one [source]'s. */
fun ActivityHistory.incomeSeries(source: String?): List<Long> {
    val history = source?.let { key -> monthly.incomeSources.firstOrNull { it.key.encode() == key } }
    return history?.months?.map { it.receivedCents } ?: monthly.months.map { it.incomeCents }
}

/** A stable, saveable token for a source, so the selection can live in the SavedStateHandle. */
fun IncomeSourceKey.encode(): String =
    when (this) {
        is IncomeSourceKey.Row -> "row:${component?.name.orEmpty()}|${extraId.orEmpty()}|${lineId.orEmpty()}"
        is IncomeSourceKey.Named -> "named:$normalised"
    }

/** One "By source" row of the income header, summed over the period's months. */
data class IncomeSourceRow(
    val key: String,
    val label: String?,
    val receivedCents: Long,
    val expectedCents: Long?,
)

/** The income header's figures for the period: what was [receivedCents], what was [expectedCents] and the sources. */
data class IncomeAnalytics(
    val receivedCents: Long,
    val expectedCents: Long?,
    val differenceCents: Long?,
    val sources: List<IncomeSourceRow>,
)

/**
 * The period's income by funding month. [IncomeAnalytics.expectedCents] is `null` unless some source expects
 * something; the difference only compares sources that expect something, so unplanned income does not hide a shortfall.
 */
fun ActivityHistory.incomeAnalytics(): IncomeAnalytics {
    val rows =
        monthly.incomeSources.mapNotNull { source ->
            val mine = source.months.filter { it.month in periodMonths }
            val expected = mine.mapNotNull { it.expectedCents }.takeIf { it.isNotEmpty() }?.sum()
            val received = mine.sumOf { it.receivedCents }
            IncomeSourceRow(source.key.encode(), source.label, received, expected)
                .takeIf { received != 0L || (expected ?: 0L) != 0L }
        }
    val expecting = rows.filter { it.expectedCents != null }
    val expected = expecting.sumOf { it.expectedCents ?: 0L }.takeIf { it > 0L }
    return IncomeAnalytics(
        receivedCents = monthly.months.filter { it.month in periodMonths }.sumOf { it.incomeCents },
        expectedCents = expected,
        differenceCents = expected?.let { expecting.sumOf { row -> row.receivedCents } - it },
        sources = rows,
    )
}

/** The Income header's figures; the Evolution chart follows the selected [source]. */
fun ActivityHistory.incomeFigures(source: String?): HistoryFigures =
    HistoryFigures(yearToDate = monthly.yearToDate.orNull(), evolution = evolutionOf(incomeSeries(source)))
