package com.joaoeoneves.fintrack.domain.model

import java.text.Normalizer
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

fun PlannedIncome.rowKey(): IncomeSourceKey.Row =
    if (isManaged) {
        IncomeSourceKey.Row(salaryComponent, salaryExtraId, null)
    } else {
        IncomeSourceKey.Row(null, null, id)
    }

/**
 * The source [income] belongs to: the row of its line by [lineOfIncome] (the one attribution rule); else [IncomeSourceKey.Named] by exact normalised source text.
 */
fun incomeSourceOf(
    income: Income,
    lines: List<PlannedIncome>,
): IncomeSourceKey =
    lineOfIncome(income, lines)?.rowKey()
        ?: IncomeSourceKey.Named(normalisedWords(income.source).joinToString(" "))

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

private class DatedIncome(
    val income: Income,
    val key: IncomeSourceKey,
    val month: YearMonth,
)

/**
 * Per-source income history over [months]: received by the month of its date, expected from
 * [incomeRowsIn] only for months up to [today], and only in months where the row exists (a line not yet valid
 * in a month has no row there, so no expected value). Line rows come first in list order, then named sources by
 * received
 * total (largest first); sources with nothing received and nothing expected are dropped.
 */
@Suppress("LongParameterList") // The inputs of one income attribution, mirrors monthlyTotalsOf.
fun incomeHistoryOf(
    months: List<YearMonth>,
    income: List<Income>,
    lines: List<PlannedIncome>,
    incomeMonth: IncomeMonth,
    zone: ZoneId,
    today: YearMonth,
): List<IncomeSourceHistory> {
    val received =
        income
            .map { DatedIncome(it, incomeSourceOf(it, lines), YearMonth.from(it.date.atZone(zone))) }
            .filter { it.month in months }
    val rowsByMonth = months.associateWith { incomeRowsIn(lines, it, incomeMonth) }
    val lineKeys =
        rowsByMonth.values
            .flatten()
            .map { it.key() }
            .distinct()
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

internal fun normalisedWords(text: String): List<String> =
    Normalizer
        .normalize(text.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }

/**
 * The line a [source] (as normalised words) belongs to: a label equal to it wins; otherwise a label the source is a
 * whole-word prefix of ("salario" of "salario liquido"). A label that is a prefix of the source never matches
 * ("salary bonus" is not "salary"). Among several qualifying labels the exact one, then the shortest, wins; a tie
 * there means no match.
 */
internal fun matchingLine(
    source: List<String>,
    lines: List<PlannedIncome>,
): PlannedIncome? {
    if (source.isEmpty()) return null
    val labelled = lines.map { it to normalisedWords(it.label) }.filter { (_, words) -> words.isNotEmpty() }
    val exact = labelled.filter { (_, words) -> words == source }
    val candidates = exact.ifEmpty { labelled.filter { (_, words) -> words.take(source.size) == source } }
    val shortest = candidates.minOfOrNull { (_, words) -> words.size }
    return candidates.filter { (_, words) -> words.size == shortest }.singleOrNull()?.first
}
