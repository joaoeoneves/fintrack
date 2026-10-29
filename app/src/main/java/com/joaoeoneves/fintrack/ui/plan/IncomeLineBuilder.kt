package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.editedFrom
import com.joaoeoneves.fintrack.domain.model.isManaged
import com.joaoeoneves.fintrack.domain.model.startingAt
import java.time.YearMonth

/** The line to save: a managed one keeps the profile's amount and schedule, any other takes the editor's. */
internal fun incomeLineOf(
    existing: PlannedIncome?,
    lines: List<PlannedIncome>,
    label: String,
    amountCents: Long,
    schedule: IncomeSchedule,
): PlannedIncome =
    if (existing != null && existing.isManaged) {
        existing.withUserEdits(label, schedule)
    } else {
        PlannedIncome(
            id = existing?.id.orEmpty(),
            label = label,
            amountCents = amountCents,
            order = existing?.order ?: ((lines.maxOfOrNull { it.order } ?: -1) + 1),
            basis = schedule.basis,
            frequency = schedule.frequency,
            monthOfYear = schedule.monthOfYear,
            yearMonth = schedule.yearMonth,
            skipMonths = schedule.skipMonths,
            autoRecord = schedule.autoRecord,
            payDay = schedule.payDay,
        )
    }

/**
 * This built line with its version history applied: a new line ([existing] null) starts in plan month
 * [effectiveFrom]; an existing one keeps its start and versions, and a changed amount is versioned from
 * [effectiveFrom] (earlier months keep the old amount, a month before its start moves the start back). A managed
 * line is left as is, the salary profile versions it.
 */
internal fun PlannedIncome.versionedFrom(
    existing: PlannedIncome?,
    effectiveFrom: YearMonth,
): PlannedIncome =
    when {
        existing == null -> startingAt(effectiveFrom)
        existing.isManaged -> this
        else ->
            copy(amountCents = existing.amountCents, startMonth = existing.startMonth, versions = existing.versions)
                .editedFrom(effectiveFrom, amountCents)
    }

/** Whether [other] switches automation or the pay day, which restarts the line's cursor. */
internal fun PlannedIncome.paysDifferently(other: PlannedIncome): Boolean =
    autoRecord != other.autoRecord ||
        payDay != other.payDay

/**
 * A managed line keeps the profile's amount and schedule; the label, [autoRecord] and
 * [payDay] are the user's.
 */
private fun PlannedIncome.withUserEdits(
    label: String,
    schedule: IncomeSchedule,
): PlannedIncome =
    copy(
        label = label,
        autoRecord = schedule.autoRecord,
        payDay = schedule.payDay,
    )
