package com.joaoeoneves.fintrack.ui.common

import java.math.BigDecimal
import kotlin.math.abs

// A query that looks like a plain amount: digits with an optional '.' or ',' and up to two
// fraction digits.
private val AMOUNT_QUERY_PATTERN = Regex("""^\d+([.,]\d{0,2})?$""")

private const val CENTS_SCALE = 2
private const val GROUP_DIGITS = 3

// Characters dropped from a query before it is read as an amount (spaces are dropped by isWhitespace).
private val IGNORED_CHARS = setOf('€', '\u00A0', '\u202F')

/** [text] with thousands separators removed, leaving at most one '.' or ',' as the decimal separator. */
private fun withoutGrouping(text: String): String {
    val lastDot = text.lastIndexOf('.')
    val lastComma = text.lastIndexOf(',')
    val decimalAt =
        when {
            lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
            lastDot >= 0 -> lastDot.takeIf { text.count { it == '.' } == 1 && text.length - it - 1 != GROUP_DIGITS }
            lastComma >= 0 -> lastComma.takeIf { text.count { it == ',' } == 1 }
            else -> null
        }
    return buildString {
        text.forEachIndexed { index, c ->
            if (index == decimalAt || (c != '.' && c != ',')) append(c)
        }
    }
}

/**
 * Whether a search [query] matches [amountCents] as an amount. The amount is rendered as a plain,
 * two-fraction-digit decimal (e.g. 1250 -> "12.50") and matched by substring against the query
 * (with ',' normalized to '.'), which has some deliberate consequences:
 * - partial matches: "12" matches 12.00 but also 112.00 and 5.12;
 * - the currency sign and any spaces (including non-breaking ones) are ignored: "12,50 €" matches 12.50;
 * - thousands grouping is understood: with both '.' and ',' the last one is the decimal separator and the other
 *   groups ("1.234,50", "1,234.50"); repeated separators group ("1.234.567"); a lone '.' followed by exactly
 *   three digits ("1.234") groups too, since an amount never has three fraction digits;
 * - a query with more than two fraction digits or any other non-numeric character never matches;
 * - matching is locale-independent: '.' and ',' are both accepted as the decimal separator;
 * - refunds match by magnitude (the sign is ignored).
 */
fun amountMatchesQuery(
    amountCents: Long,
    query: String,
): Boolean {
    val trimmed = query.filterNot { it.isWhitespace() || it in IGNORED_CHARS }
    val normalized = withoutGrouping(trimmed).replace(',', '.')
    if (!AMOUNT_QUERY_PATTERN.matches(normalized)) return false
    return BigDecimal.valueOf(abs(amountCents), CENTS_SCALE).toPlainString().contains(normalized)
}

/** Case-insensitive substring check that treats a null receiver as no match. */
fun String?.containsIgnoreCase(q: String): Boolean = this?.contains(q, ignoreCase = true) ?: false
