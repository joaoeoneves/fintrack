package com.joaoeoneves.fintrack.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private const val TOKEN_CURRENT = "current"
private const val PREFIX_MONTH = "month"
private const val PREFIX_ROLLING = "rolling"
private const val PREFIX_CUSTOM = "custom"
private const val TOKEN_SEPARATOR = ':'

/**
 * Encodes this period as a stable, primitive string token (used as a navigation route argument and
 * in `SavedStateHandle`): `"current"`, `"month:2026-09"`, `"rolling:THREE_MONTHS"` or
 * `"custom:2026-10-01:2026-10-15"`. Round-trips through [decodePeriod].
 */
fun Period.encode(): String =
    when (this) {
        Period.CurrentMonth -> TOKEN_CURRENT
        is Period.Month -> "$PREFIX_MONTH$TOKEN_SEPARATOR$yearMonth"
        is Period.Rolling -> "$PREFIX_ROLLING$TOKEN_SEPARATOR${range.name}"
        is Period.Custom -> "$PREFIX_CUSTOM$TOKEN_SEPARATOR$start$TOKEN_SEPARATOR$endInclusive"
    }

/**
 * Decodes a token produced by [encode]. Lenient by design, since tokens can outlive the app
 * version or the month they were stored in: `null`, malformed tokens and unknown rolling ranges
 * decode to [Period.CurrentMonth], months that are now current or in the future normalize to
 * [Period.CurrentMonth], and custom ranges with start after end are swapped.
 */
fun decodePeriod(
    token: String?,
    now: Instant,
    zone: ZoneId,
): Period {
    val parts = token?.split(TOKEN_SEPARATOR).orEmpty()
    val decoded =
        runCatching {
            when (parts.firstOrNull()) {
                PREFIX_MONTH -> parts.singleArg()?.let { Period.ofMonth(YearMonth.parse(it), now, zone) }
                PREFIX_ROLLING -> parts.singleArg()?.let { Period.Rolling(TimeRange.valueOf(it)) }
                PREFIX_CUSTOM -> parts.toCustomPeriod()
                else -> null
            }
        }.getOrNull()
    return decoded ?: Period.CurrentMonth
}

/** The single argument after the prefix, or `null` if the token doesn't have exactly one. */
private fun List<String>.singleArg(): String? = if (size == 2) this[1] else null

/** Parses `["custom", start, end]`, swapping the ends if they are reversed. */
private fun List<String>.toCustomPeriod(): Period.Custom? {
    if (size != CUSTOM_TOKEN_PARTS) return null
    val first = LocalDate.parse(this[1])
    val second = LocalDate.parse(this[2])
    return if (first <= second) Period.Custom(first, second) else Period.Custom(second, first)
}

private const val CUSTOM_TOKEN_PARTS = 3
