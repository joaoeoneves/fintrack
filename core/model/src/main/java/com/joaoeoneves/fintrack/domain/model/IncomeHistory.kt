package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth
import java.time.ZoneId

/** What an income belongs to in the history: a row of the planned income list, or a free-text source. */
sealed interface IncomeSourceKey {
    /**
     * A row of the income list: a managed [component] (with [extraId] for an extra), or a manual line
     * (`component == null`, identified by [lineId]).
     */
    data class Row(
        val component: SalaryComponent?,
        val extraId: String?,
        val lineId: String?,
    ) : IncomeSourceKey

    /** A source matching no line; [normalised] is the trimmed, accent- and case-folded source text. */
    data class Named(
        val normalised: String,
    ) : IncomeSourceKey
}

private fun PlannedIncome.rowKey(): IncomeSourceKey.Row =
    if (isManaged) {
        IncomeSourceKey.Row(salaryComponent, salaryExtraId, null)
    } else {
        IncomeSourceKey.Row(null, null, id)
    }

/**
 * The source [income] belongs to: a payday income (`pay_<lineId>_<yyyyMM>`) to its line's row; else a source
 * matching a line label (case/accent-insensitive, equal or whole-word prefix, like [isSalaryPayment]) to that line's
 * row; else [IncomeSourceKey.Named] by exact normalised source text.
 */
fun incomeSourceOf(
    income: Income,
    lines: List<PlannedIncome>,
): IncomeSourceKey {
    val paydayLine = parsePaydayIncomeId(income.id)?.let { (lineId, _) -> lines.firstOrNull { it.id == lineId } }
    val words = normalisedWords(income.source)
    val labelLine =
        if (words.isEmpty()) null else lines.firstOrNull { isWordPrefixMatch(words, normalisedWords(it.label)) }
    return (paydayLine ?: labelLine)?.rowKey() ?: IncomeSourceKey.Named(words.joinToString(" "))
}

/** One month of a source: what was [receivedCents] against what was [expectedCents] (`null` when not expected yet). */
data class SourceMonth(
    val month: YearMonth,
    val receivedCents: Long,
    val expectedCents: Long?,
)

/** A source's history over the window. [label] is the line's label or the latest spelling of the source. */
data class IncomeSourceHistory(
    val key: IncomeSourceKey,
    val label: String?,
    val months: List<SourceMonth>,
) {
    val receivedCents: Long get() = months.sumOf { it.receivedCents }
    val expectedCents: Long get() = months.sumOf { it.expectedCents ?: 0L }
}

private fun IncomeRow.key(): IncomeSourceKey = lines.first().rowKey()

private class FundedIncome(
    val income: Income,
    val key: IncomeSourceKey,
    val month: YearMonth,
)

/**
 * Per-source income history over [months]: received by funding month (see [fundingMonthOf]), expected from
 * [incomeRowsIn] only for months up to [today]. Line rows come first in list order, then named sources by received
 * total (largest first); sources with nothing received and nothing expected are dropped.
 */
@Suppress("LongParameterList") // The inputs of one income attribution, mirrors monthlyTotalsOf.
fun incomeHistoryOf(
    months: List<YearMonth>,
    income: List<Income>,
    lines: List<PlannedIncome>,
    funding: IncomeFunding,
    zone: ZoneId,
    today: YearMonth,
): List<IncomeSourceHistory> {
    val received =
        income
            .map { FundedIncome(it, incomeSourceOf(it, lines), fundingMonthOf(it, lines, funding, zone)) }
            .filter { it.month in months }
    val rowsByMonth = months.associateWith { incomeRowsIn(lines, it, funding) }
    val lineKeys =
        rowsByMonth.values
            .firstOrNull()
            .orEmpty()
            .map { it.key() }
    val namedKeys =
        received
            .map { it.key }
            .filterIsInstance<IncomeSourceKey.Named>()
            .distinct()
            .sortedByDescending { key -> received.filter { it.key == key }.sumOf { it.income.amountCents } }
    return (lineKeys + namedKeys).distinct().mapNotNull { key ->
        val mine = received.filter { it.key == key }
        val sourceMonths =
            months.map { month ->
                val expected =
                    if (month > today) null else rowsByMonth[month].orEmpty().firstOrNull { it.key() == key }?.cents
                SourceMonth(month, mine.filter { it.month == month }.sumOf { it.income.amountCents }, expected)
            }
        val label =
            when (key) {
                is IncomeSourceKey.Row ->
                    rowsByMonth.values
                        .flatten()
                        .firstOrNull { it.key() == key }
                        ?.lines
                        ?.first()
                        ?.label
                is IncomeSourceKey.Named ->
                    mine
                        .maxByOrNull { it.income.date }
                        ?.income
                        ?.source
                        ?.trim()
            }
        IncomeSourceHistory(key, label, sourceMonths).takeIf { it.receivedCents != 0L || it.expectedCents != 0L }
    }
}
