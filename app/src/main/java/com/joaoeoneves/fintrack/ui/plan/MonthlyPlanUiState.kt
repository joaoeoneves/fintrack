package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.ui.common.UiText
import java.time.LocalDate
import java.time.YearMonth

/**
 * The common payday (`null` when rows pay on different days) and the receipts of [Content.period]'s month:
 * [receivedOn] holds the day each fully received income row
 * (keyed by [key]) was received; [canReceive] holds the keys of the rows whose pay date is today or past (a row
 * paid later cannot be received yet); [today] caps the date of a receipt.
 */
data class IncomeReceipts(
    val payday: LocalDate?,
    val today: LocalDate,
    val canReceive: Set<String>,
    val receivedOn: Map<String, LocalDate> = emptyMap(),
    /** The day the month's salary (or, without a salary line, every due line) was actually received; else `null`. */
    val receivedPayday: LocalDate? = null,
    /** What each received row (keyed by [key]) actually brought in; may differ from its planned amount. */
    val receivedCents: Map<String, Long> = emptyMap(),
)

/**
 * The day the month's pay was actually received: the salary row's received day, or, when there is no salary row
 * paying this month, the latest received day once every paying row is received. `null` while not received.
 */
fun receivedPaydayOf(
    rows: List<IncomeRow>,
    receivedOn: Map<String, LocalDate>,
): LocalDate? {
    val paying = rows.filter { it.cents > 0L }
    val salary = paying.firstOrNull { it.component == SalaryComponent.SALARY }
    return if (salary != null) {
        receivedOn[salary.key]
    } else {
        val days = paying.map { receivedOn[it.key] }
        if (days.isNotEmpty() && days.all { it != null }) days.filterNotNull().max() else null
    }
}

/** The row's identity within a month's list: the id of its first line. */
val IncomeRow.key: String get() = lines.first().id

/**
 * The working days of [month]: [days] is what plans use (the override when [overridden], else the calendar
 * count minus [daysOff]); [calendarDays] is the plain calendar count (holidays out, days off not) and
 * [holidays] how many weekdays of the month are holidays.
 */
data class WorkingDaysState(
    val month: YearMonth,
    val days: Int,
    val daysOff: Int = 0,
    val calendarDays: Int,
    val holidays: Int,
    val overridden: Boolean,
)

/** When and how an income line pays; the part of the line the editor sheet adds beyond label and amount. */
data class IncomeSchedule(
    val basis: IncomeBasis = IncomeBasis.FIXED,
    val frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
    val monthOfYear: Int? = null,
    val yearMonth: YearMonth? = null,
    val skipMonths: Set<Int> = emptySet(),
    val autoRecord: Boolean = false,
    val payDay: Int? = null,
    /** With a fixed [payDay]: a weekend or holiday moves the pay date to the previous working day. */
    val payDayEarlierIfClosed: Boolean = false,
) {
    /**
     * Keeps only the fields that belong to [frequency]; a missing month
     * falls back to [fallback]'s.
     */
    fun normalized(fallback: YearMonth): IncomeSchedule =
        when (frequency) {
            IncomeFrequency.MONTHLY -> copy(monthOfYear = null, yearMonth = null)
            IncomeFrequency.YEARLY ->
                copy(
                    monthOfYear = monthOfYear ?: fallback.monthValue,
                    yearMonth = null,
                    skipMonths = emptySet(),
                )
            IncomeFrequency.ONCE ->
                copy(monthOfYear = null, yearMonth = yearMonth ?: fallback, skipMonths = emptySet())
        }
}

/**
 * The income-line sheet: [editingId] is `null` for a new line. [labelError] is set when the last
 * save attempt was rejected, and the dialog stays open.
 */
data class IncomeLineEditorState(
    val editingId: String?,
    val initialLabel: String,
    val initialAmountCents: Long?,
    val initialSchedule: IncomeSchedule = IncomeSchedule(),
    val labelError: UiText? = null,
    /** A line generated from the salary profile: only its label and earmark can be changed here. */
    val managed: Boolean = false,
    /** The salary component of a managed line, whose name replaces the label in the sheet. */
    val component: SalaryComponent? = null,
    /** Set once a save was held back because the line looks like the profile's salary; the next save goes through. */
    val looksLikeSalary: Boolean = false,
    /** The line's previous amount ("300 until September 2026"), when it has one. */
    val history: AmountHistoryNote? = null,
)
