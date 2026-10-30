package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_DAYS_OFF
import com.joaoeoneves.fintrack.domain.model.withoutBacklog
import java.time.LocalDate

/** Schema version (guest file and backup) that dropped goals, budgets, envelope funding and the manual flow. */
internal const val SIMPLIFIED_SCHEMA_VERSION = 23

/**
 * The recurring items of a pre-23 data set, upgraded: an item that waited for manual confirmation (`autoPost`
 * false or absent) gets everything strictly before [today] marked handled (see `withoutBacklog`), so becoming
 * automatic does not post its backlog; today's occurrence still posts. The marker is dropped from every record.
 */
internal fun List<LocalRecurringItem>.upgradedTo23(today: LocalDate): List<LocalRecurringItem> =
    map { item ->
        val adopted =
            if (item.autoPost == true) {
                item
            } else {
                item.toDomainOrNull()?.withoutBacklog(today)?.toLocal()?.let { moved ->
                    item.copy(handledThrough = moved.handledThrough)
                } ?: item
            }
        adopted.copy(autoPost = null)
    }

/** The month records that still hold something: one that only carried a manual working-days count is gone. */
internal fun List<LocalMonthSettings>.upgradedTo23(): List<LocalMonthSettings> =
    filter { it.legacyDaysOff.let { legacy -> legacy != null && legacy > 0 } || it.totalDays > 0 }

/**
 * The 22 -> 23 upgrade of the guest file: see the two list upgrades above. The removed fields (goals, budgets, envelope
 * funding, the working-days override, `salaryPaysNextMonth`) are unknown keys now and vanish on decode. Idempotent.
 */
internal fun LocalDataDocument.upgradedTo23(today: LocalDate = LocalDate.now()): LocalDataDocument =
    copy(
        schemaVersion = SIMPLIFIED_SCHEMA_VERSION,
        recurring = recurring.upgradedTo23(today),
        monthSettings = monthSettings.upgradedTo23(),
    )

/** Schema version (guest file and backup) that split a month's days off into vacation, sick and other. */
internal const val DAYS_OFF_BY_TYPE_SCHEMA_VERSION = 24

/** The pre-24 single `daysOff` total becomes vacation days; the legacy marker is dropped. Idempotent. */
internal fun List<LocalMonthSettings>.upgradedTo24(): List<LocalMonthSettings> =
    map { record ->
        val legacy = record.legacyDaysOff
        if (legacy == null) record else record.copy(vacationDays = record.vacationDays + legacy, legacyDaysOff = null)
    }.filter { it.totalDays > 0 }

/** Schema version (guest file and backup) that added the optional expense `refundOf`/`splitId` links. */
internal const val EXPENSE_LINKS_SCHEMA_VERSION = 25

/** Schema version (guest file and backup) that folded sick days into other days and added expense `awaitingRefund`. */
internal const val DAYS_OFF_TWO_TYPES_SCHEMA_VERSION = 26

/**
 * The pre-26 `sickDays` count is added to the other days, trimming them so the month's total stays within
 * [MAX_DAYS_OFF]; the marker is dropped and months left with nothing are removed. Idempotent.
 */
internal fun List<LocalMonthSettings>.upgradedTo26(): List<LocalMonthSettings> =
    map { record ->
        val sick = record.legacySickDays
        if (sick == null) {
            record
        } else {
            val room = (MAX_DAYS_OFF - record.vacationDays).coerceAtLeast(0)
            record.copy(otherDays = (record.otherDays + sick).coerceIn(0, room), legacySickDays = null)
        }
    }.filter { it.totalDays > 0 }

/** The 25 -> 26 upgrade of the guest file: see the month-settings upgrade above. */
internal fun LocalDataDocument.upgradedTo26(): LocalDataDocument =
    copy(schemaVersion = DAYS_OFF_TWO_TYPES_SCHEMA_VERSION, monthSettings = monthSettings.upgradedTo26())

/** The 23 -> 24 upgrade of the guest file: see the month-settings upgrade above. */
internal fun LocalDataDocument.upgradedTo24(): LocalDataDocument =
    copy(schemaVersion = DAYS_OFF_BY_TYPE_SCHEMA_VERSION, monthSettings = monthSettings.upgradedTo24())
