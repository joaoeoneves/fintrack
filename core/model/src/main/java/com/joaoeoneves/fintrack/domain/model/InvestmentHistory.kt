package com.joaoeoneves.fintrack.domain.model

import java.text.Normalizer
import java.time.YearMonth
import java.time.ZoneId

/**
 * What one investment instrument is called across months: the expense name trimmed, lower-cased, without accents and
 * with collapsed whitespace; an expense without a name falls back to its category (`category:<id>`).
 */
@JvmInline
value class InstrumentKey(
    val value: String,
)

/**
 * The money put into one instrument. [label] is the latest spelling of its name (`null` when it has none, then the
 * UI shows the category of [categoryId]).
 */
data class InstrumentAmount(
    val key: InstrumentKey,
    val label: String?,
    val categoryId: CategoryId,
    val cents: Long,
)

/** One month of investing: [totalCents] is the sum of its [instruments]. */
data class InvestmentMonth(
    val month: YearMonth,
    val totalCents: Long,
    val instruments: List<InstrumentAmount>,
)

/**
 * The investments (net outflow of SAVING-kind categories) of the months in [months], oldest first: per month and
 * per [instruments] (the whole window, biggest first) plus the [movements] (newest first).
 */
data class InvestmentHistory(
    val months: List<InvestmentMonth>,
    val instruments: List<InstrumentAmount>,
    val movements: List<Expense>,
) {
    /** The total of every month of the window. */
    val totalCents: Long get() = months.sumOf { it.totalCents }

    /** The [InvestmentMonth] of [month], `null` outside the window. */
    fun monthOf(month: YearMonth): InvestmentMonth? = months.firstOrNull { it.month == month }
}

/**
 * The [InvestmentHistory] of [months] over the SAVING-kind expenses (kinds come from the [categories] catalog).
 * Refunds net; each instrument's month is floored at 0. Expenses are attributed by their date in [zone]; those
 * outside [months] are ignored.
 */
fun investmentHistoryOf(
    months: List<YearMonth>,
    expenses: List<Expense>,
    categories: List<Category>,
    zone: ZoneId,
): InvestmentHistory {
    val window = months.toSet()
    val saving =
        expenses
            .filter { categories.resolve(it.category.id).kind == CategoryKind.SAVING }
            .filter { YearMonth.from(it.date.atZone(zone)) in window }
    val latest = saving.sortedByDescending { it.date }
    val newest = latest.groupBy { it.instrumentKey() }.mapValues { (_, list) -> list.first() }
    val byMonth = saving.groupBy { YearMonth.from(it.date.atZone(zone)) }
    val monthRows =
        months.map { month ->
            val rows = amountsOf(byMonth[month].orEmpty(), newest)
            InvestmentMonth(month, rows.sumOf { it.cents }, rows)
        }
    val perInstrument =
        monthRows
            .flatMap { it.instruments }
            .groupBy { it.key }
            .map { (_, rows) -> rows.first().copy(cents = rows.sumOf { it.cents }) }
            .sortedByDescending { it.cents }
    return InvestmentHistory(monthRows, perInstrument, latest)
}

private fun amountsOf(
    expenses: List<Expense>,
    newest: Map<InstrumentKey, Expense>,
): List<InstrumentAmount> =
    expenses
        .groupBy { it.instrumentKey() }
        .mapNotNull { (key, list) ->
            val cents = list.netCents()
            val latest = newest.getValue(key)
            InstrumentAmount(key, latest.name.trim().takeIf { it.isNotEmpty() }, latest.category.id, cents)
                .takeIf { cents > 0L }
        }.sortedByDescending { it.cents }

/** The instrument this expense belongs to (see [InstrumentKey]). */
fun Expense.instrumentKey(): InstrumentKey {
    val normalised =
        Normalizer
            .normalize(name.trim(), Normalizer.Form.NFD)
            .replace(ACCENTS, "")
            .lowercase()
            .replace(WHITESPACE, " ")
    return InstrumentKey(normalised.ifEmpty { "category:${category.id.value}" })
}

private val ACCENTS = Regex("\\p{M}+")
private val WHITESPACE = Regex("\\s+")
