package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import java.time.YearMonth
import java.time.format.DateTimeParseException

internal fun LocalAutomation.toDomain(): AutomationSettings =
    AutomationSettings(
        paydayEnabled = paydayEnabled,
        // An unreadable cursor reads as "never recorded", which the automation treats as off until re-enabled.
        paydayRecordedThrough = paydayThrough?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
    )

internal fun AutomationSettings.toLocal(): LocalAutomation =
    LocalAutomation(paydayEnabled = paydayEnabled, paydayThrough = paydayRecordedThrough?.toString())

/** This record when valid, else `null`. */
internal fun LocalAutomation.validOrNull() = takeIf { runCatching { validateAutomation(it) }.isSuccess }

internal fun validateAutomation(automation: LocalAutomation) {
    automation.paydayThrough?.let {
        try {
            YearMonth.parse(it)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Payday cursor is not a year-month: $it", e)
        }
    }
}
