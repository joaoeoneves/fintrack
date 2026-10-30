package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.IncomeSourceKey
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.incomeSourceOf
import com.joaoeoneves.fintrack.domain.model.rowKey

/** Income per month (aligned to [MonthlyHistory.months]): everything funding the month, or one [source]'s. */
fun ActivityHistory.incomeSeries(source: String?): List<Long> {
    val history = source?.let { key -> monthly.incomeSources.firstOrNull { it.key.encode() == key } }
    val shown = monthly.months.map { it.month }.toSet()
    return history?.months?.filter { it.month in shown }?.map { it.receivedCents }
        ?: monthly.months.map { it.incomeCents }
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
    val pastShortCents: Long = 0L,
)

/** The income header's figures for the period: what was [receivedCents], what was [expectedCents] and the sources. */
data class IncomeAnalytics(
    val receivedCents: Long,
    val expectedCents: Long?,
    val missingCents: Long?,
    val sources: List<IncomeSourceRow>,
)

/**
 * The period's income by funding month. [IncomeAnalytics.expectedCents] is `null` unless some source expects
 * something; [IncomeAnalytics.missingCents] is what the expecting sources still lack (`null` when nothing). A source is
 * short only for months before [ActivityHistory.today] (all months when it is unknown).
 */
fun ActivityHistory.incomeAnalytics(): IncomeAnalytics {
    val rows =
        monthly.incomeSources.mapNotNull { source ->
            val mine = source.months.filter { it.month in periodMonths }
            val expected = mine.mapNotNull { it.expectedCents }.takeIf { it.isNotEmpty() }?.sum()
            val received = mine.sumOf { it.receivedCents }
            val pastShort =
                mine
                    .filter { today == null || it.month < today }
                    .sumOf { (it.expectedCents ?: 0L) - it.receivedCents }
            IncomeSourceRow(source.key.encode(), source.label, received, expected, maxOf(0L, pastShort))
                .takeIf { received != 0L || (expected ?: 0L) != 0L }
        }
    val receivedCents = monthly.months.filter { it.month in periodMonths }.sumOf { it.incomeCents }
    val expecting = rows.filter { it.expectedCents != null }
    val expected = expecting.sumOf { it.expectedCents ?: 0L }.takeIf { it > 0L }
    return IncomeAnalytics(
        receivedCents = receivedCents,
        expectedCents = expected,
        missingCents =
            expected?.let { expecting.sumOf { maxOf(0L, (it.expectedCents ?: 0L) - it.receivedCents) } }
                ?.takeIf { it > 0L },
        sources = rows,
    )
}

/** The Income header's figures; the Evolution chart follows the selected [source]. */
fun ActivityHistory.incomeFigures(source: String?): HistoryFigures {
    val evolution = evolutionOf(incomeSeries(source))
    return HistoryFigures(evolution = evolution)
}

/** One "Source" filter chip of the Income tab: the encoded [key] of a source and its display [label]. */
data class IncomeSourceChip(
    val key: String,
    val label: String?,
)

/**
 * The distinct sources of [incomes] (by [incomeSourceOf]), biggest first. A line's label wins for a row source and the
 * latest spelling for a free-text one; a `null` label means the source has no name (shown as "Other").
 */
fun periodIncomeSourcesOf(
    incomes: List<Income>,
    lines: List<PlannedIncome>,
): List<IncomeSourceChip> =
    incomes
        .groupBy { incomeSourceOf(it, lines) }
        .map { (key, mine) ->
            val label =
                when (key) {
                    is IncomeSourceKey.Row -> lines.firstOrNull { it.rowKey() == key }?.label
                    is IncomeSourceKey.Named -> mine.maxByOrNull { it.date }?.source?.trim()
                }
            Triple(key, label?.takeIf { it.isNotBlank() }, mine.sumOf { it.amountCents })
        }.sortedByDescending { it.third }
        .map { (key, label, _) -> IncomeSourceChip(key.encode(), label) }
