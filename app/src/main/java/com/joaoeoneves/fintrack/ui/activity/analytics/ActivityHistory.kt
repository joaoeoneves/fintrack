package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.SpendingComparison
import com.joaoeoneves.fintrack.domain.model.YearToDate
import com.joaoeoneves.fintrack.domain.model.spendingComparisonOf
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.usecase.MonthlyHistory
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Fewest bars the Evolution chart shows, however short the period is. */
const val MIN_EVOLUTION_MONTHS = 6

/**
 * The monthly history behind the Activity header: [monthly] ends at the period's last month, [periodMonths] are the
 * calendar months the period touches and [calendarMonth] tells a single calendar month from a rolling/custom range.
 */
data class ActivityHistory(
    val periodMonths: List<YearMonth>,
    val calendarMonth: Boolean,
    val monthly: MonthlyHistory,
)

/** The months [period] touches, oldest first (a rolling/custom range lists every calendar month it overlaps). */
fun periodMonthsOf(
    period: Period,
    now: Instant,
    zone: ZoneId,
): List<YearMonth> {
    val range = period.toInstantRange(now, zone)
    val first = YearMonth.from(range.startInclusive.atZone(zone))
    val last = YearMonth.from(range.endExclusive.minusMillis(1).atZone(zone))
    return generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.toList()
}

/** Spending of the period's single month against the previous month and the recent average; `null` otherwise. */
fun ActivityHistory.comparison(): SpendingComparison? =
    if (calendarMonth && periodMonths.size == 1) spendingComparisonOf(monthly.months, periodMonths.single()) else null

/** The average monthly spending over the period's months that have data; `null` for one month or no data. */
fun ActivityHistory.monthlyAverageSpendingCents(): Long? {
    val withData = monthly.months.filter { it.month in periodMonths && it.hasData }
    val total = withData.sumOf { it.spendingCents }
    return if (periodMonths.size > 1 && withData.isNotEmpty()) total / withData.size else null
}

/**
 * Spending per month (aligned to [MonthlyHistory.months]): the total spending, or - when a [category] or an
 * envelope's [scope] of categories is selected - what was spent in it. A category wins over the envelope.
 */
fun ActivityHistory.spendingSeries(
    category: CategoryId?,
    scope: Set<CategoryId>?,
): List<Long> {
    val ids = category?.let(::setOf) ?: scope ?: return monthly.months.map { it.spendingCents }
    return monthly.months.indices.map { i -> ids.sumOf { monthly.categoryMonthly[it]?.getOrNull(i) ?: 0L } }
}

/** The bars of the Evolution chart: [months] with their [valuesCents] and the indices inside the period. */
data class Evolution(
    val months: List<YearMonth>,
    val valuesCents: List<Long>,
    val highlighted: Set<Int>,
)

/**
 * The last `max(6, period months)` entries of [valuesCents] (aligned to the history months), ending at the period's
 * last month; `null` when every value is zero (nothing to draw).
 */
fun ActivityHistory.evolutionOf(valuesCents: List<Long>): Evolution? {
    val count = maxOf(MIN_EVOLUTION_MONTHS, periodMonths.size)
    val months = monthly.months.map { it.month }.takeLast(count)
    val values = valuesCents.takeLast(count)
    return if (values.all { it == 0L }) {
        null
    } else {
        Evolution(months, values, months.indices.filter { months[it] in periodMonths }.toSet())
    }
}

/** What the headers' history parts show; every part is `null` when it has no data. */
data class HistoryFigures(
    val comparison: SpendingComparison? = null,
    val monthlyAverageCents: Long? = null,
    val yearToDate: YearToDate? = null,
    val evolution: Evolution? = null,
) {
    val hasDetails: Boolean get() = yearToDate != null || evolution != null
}

internal fun YearToDate.orNull(): YearToDate? = takeIf { spentCents != 0L || incomeCents != 0L || savedCents != 0L }

/** The Expenses/All header's figures; the Evolution chart follows the active [category] or envelope [scope]. */
fun ActivityHistory.spendingFigures(
    category: CategoryId?,
    scope: Set<CategoryId>?,
): HistoryFigures =
    HistoryFigures(
        comparison = comparison()?.takeIf { it.previousCents != null || it.averageCents != null },
        monthlyAverageCents = monthlyAverageSpendingCents(),
        yearToDate = monthly.yearToDate.orNull(),
        evolution = evolutionOf(spendingSeries(category, scope)),
    )
