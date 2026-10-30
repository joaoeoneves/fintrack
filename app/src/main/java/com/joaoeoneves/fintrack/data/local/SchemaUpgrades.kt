package com.joaoeoneves.fintrack.data.local

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

/** The month records that still hold something: a record that only carried a manual working-days count is gone. */
internal fun List<LocalMonthSettings>.upgradedTo23(): List<LocalMonthSettings> = filter { it.daysOff > 0 }

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
