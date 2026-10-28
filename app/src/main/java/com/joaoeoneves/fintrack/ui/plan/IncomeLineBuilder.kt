package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.isManaged

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
            earmark = schedule.earmark,
            autoRecord = schedule.autoRecord,
            payDay = schedule.payDay,
        )
    }

/** Whether [other] switches automation or the pay day, which restarts the line's cursor. */
internal fun PlannedIncome.paysDifferently(other: PlannedIncome): Boolean =
    autoRecord != other.autoRecord ||
        payDay != other.payDay

/**
 * A managed line keeps the profile's amount and schedule; the label, (monthly lines') earmark, [autoRecord] and
 * [payDay] are the user's.
 */
private fun PlannedIncome.withUserEdits(
    label: String,
    schedule: IncomeSchedule,
): PlannedIncome =
    copy(
        label = label,
        earmark = if (frequency == IncomeFrequency.MONTHLY) schedule.earmark else earmark,
        autoRecord = schedule.autoRecord,
        payDay = schedule.payDay,
    )
