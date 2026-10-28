package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import java.time.YearMonth
import java.time.format.DateTimeParseException

private const val MAX_LINE_CURSORS = 64

internal fun LocalAutomation.toDomain(): AutomationSettings =
    AutomationSettings(
        legacyPaydayEnabled = paydayEnabled,
        // An unreadable cursor reads as "never recorded", which the automation treats as off until re-enabled.
        legacyPaydayRecordedThrough = paydayThrough?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
        lineRecordedThrough =
            lineThrough.mapNotNull { (id, raw) -> parseYearMonthOrNull(raw)?.let { id to it } }.toMap(),
    )

internal fun AutomationSettings.toLocal(): LocalAutomation =
    LocalAutomation(
        paydayEnabled = legacyPaydayEnabled,
        paydayThrough = legacyPaydayRecordedThrough?.toString(),
        lineThrough = lineRecordedThrough.mapValues { it.value.toString() },
    )

/** This record when valid, else `null`. */
internal fun LocalAutomation.validOrNull() = takeIf { runCatching { validateAutomation(it) }.isSuccess }

internal fun validateAutomation(automation: LocalAutomation) {
    automation.paydayThrough?.let { requireYearMonth(it) }
    require(automation.lineThrough.size <= MAX_LINE_CURSORS) { "At most $MAX_LINE_CURSORS line cursors" }
    automation.lineThrough.forEach { (id, raw) ->
        require(id.isNotBlank()) { "Line cursor id is blank" }
        requireYearMonth(raw)
    }
}

private fun requireYearMonth(raw: String) {
    try {
        YearMonth.parse(raw)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Cursor is not a year-month: $raw", e)
    }
}
