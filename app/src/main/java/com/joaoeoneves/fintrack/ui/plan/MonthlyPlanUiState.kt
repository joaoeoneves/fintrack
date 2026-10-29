package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.Earmark
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
 * (keyed by [key]) was received; [canReceive] is false for future months; [today] caps the date of a receipt.
 */
data class IncomeReceipts(
    val payday: LocalDate?,
    val today: LocalDate,
    val canReceive: Boolean,
    val receivedOn: Map<String, LocalDate> = emptyMap(),
)

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
    val earmark: Earmark? = null,
    val autoRecord: Boolean = false,
    val payDay: Int? = null,
) {
    /**
     * Keeps only the fields that belong to [frequency] (only monthly lines can be earmarked); a missing month
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
                    earmark = null,
                )
            IncomeFrequency.ONCE ->
                copy(monthOfYear = null, yearMonth = yearMonth ?: fallback, skipMonths = emptySet(), earmark = null)
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
