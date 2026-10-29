package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/**
 * The one rule that says which planned line a recorded [income] belongs to, used by every screen:
 * 1. a payday id (`pay_<lineId>_<yyyyMM>`) belongs to that line;
 * 2. else a source whose normalised text (case, accents, spaces) equals a line's label belongs to the first such line;
 * 3. else the whole-word prefix rule of [matchingLine].
 * `null` when no line claims it.
 */
fun lineOfIncome(
    income: Income,
    lines: List<PlannedIncome>,
): PlannedIncome? {
    val byPayday = parsePaydayIncomeId(income.id)?.let { (lineId, _) -> lines.firstOrNull { it.id == lineId } }
    val words = normalisedWords(income.source)
    return byPayday
        ?: lines.firstOrNull { words.isNotEmpty() && normalisedWords(it.label) == words }
        ?: matchingLine(words, lines)
}

/**
 * The incomes of [month] each line received, keyed by line id (a line with nothing received has no entry). [incomes]
 * are the month's records; a payday id of another month is left out. Each income goes to at most one line, by
 * [lineOfIncome].
 */
fun attributeIncomes(
    month: YearMonth,
    incomes: Collection<Income>,
    lines: List<PlannedIncome>,
): Map<String, List<Income>> =
    incomes
        .filter { income -> parsePaydayIncomeId(income.id)?.second?.let { it == month } ?: true }
        .mapNotNull { income -> lineOfIncome(income, lines)?.let { it.id to income } }
        .groupBy({ it.first }, { it.second })
