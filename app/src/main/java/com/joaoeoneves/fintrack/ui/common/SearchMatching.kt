package com.joaoeoneves.fintrack.ui.common

import java.math.BigDecimal
import kotlin.math.abs

// A query that looks like a plain amount: digits with an optional '.' or ',' and up to two
// fraction digits.
private val AMOUNT_QUERY_PATTERN = Regex("""^\d+([.,]\d{0,2})?$""")

private const val CENTS_SCALE = 2

/**
 * Whether a search [query] matches [amountCents] as an amount. The amount is rendered as a plain,
 * two-fraction-digit decimal (e.g. 1250 -> "12.50") and matched by substring against the query
 * (with ',' normalized to '.'), which has some deliberate consequences:
 * - partial matches: "12" matches 12.00 but also 112.00 and 5.12;
 * - grouping separators are not supported ("1,234.00" never matches), and a query with more than
 *   two fraction digits or any non-numeric character never matches;
 * - matching is locale-independent: '.' and ',' are both accepted as the decimal separator;
 * - refunds match by magnitude (the sign is ignored).
 */
fun amountMatchesQuery(
    amountCents: Long,
    query: String,
): Boolean {
    val trimmed = query.trim()
    if (!AMOUNT_QUERY_PATTERN.matches(trimmed)) return false
    val normalized = trimmed.replace(',', '.')
    return BigDecimal.valueOf(abs(amountCents), CENTS_SCALE).toPlainString().contains(normalized)
}

/** Case-insensitive substring check that treats a null receiver as no match. */
fun String?.containsIgnoreCase(q: String): Boolean = this?.contains(q, ignoreCase = true) ?: false
