package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.DEFAULT_CHRISTMAS_SUBSIDY_MONTH
import com.joaoeoneves.fintrack.domain.model.DEFAULT_HOLIDAY_SUBSIDY_MONTH
import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.MealPayment
import com.joaoeoneves.fintrack.domain.model.SPREAD_HALF_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryBreakdown
import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountScope
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryExtraDeductions
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryYearSummary
import java.time.YearMonth

/** The fields of the salary form that can hold an invalid value. */
enum class SalaryField {
    BASE,
    IRS_BASE,
    IRS_SUBSIDIES,
    SS,
    OTHER_DEDUCTIONS,
    EXEMPT,
    NET_MONTHLY,
    NET_HOLIDAY,
    NET_CHRISTMAS,
    MEAL,
}

/** The fields of the extra editor that can hold an invalid or missing value. */
enum class SalaryExtraField { NAME, AMOUNT, FEE }

/**
 * What the user has typed in the salary profile screen: amounts as cents (0 = not set), rates as text (a rate is a
 * percent, the
 * extras are kept as parsed [SalaryExtra]s), the rest as choices. Blank optional fields mean "not set".
 */
data class SalaryForm(
    val mode: SalaryInputMode = SalaryInputMode.GROSS,
    val base: Long = 0L,
    val spreadPpm: Int = SPREAD_HALF_PPM,
    val irsBase: String = "",
    val irsSubsidies: String = "",
    val ss: String = "",
    val exempt: String = "",
    val netMonthly: Long = 0L,
    val netHoliday: Long = 0L,
    val netChristmas: Long = 0L,
    val meal: Long = 0L,
    /** The (up to two) months the subsidies are paid in, oldest selection first. */
    val subsidyMonths: List<Int> = listOf(DEFAULT_HOLIDAY_SUBSIDY_MONTH, DEFAULT_CHRISTMAS_SUBSIDY_MONTH),
    val extras: List<SalaryExtra> = emptyList(),
    /** Where the user works; `null` (never set) reads as the private sector. */
    val sector: EmploymentSector? = null,
    val mealPayment: MealPayment = MealPayment.CARD,
    /** ADSE, SAMS, union dues and similar, as a percent of the gross (GROSS mode). */
    val otherDeductions: String = "",
)

/** The sector the form stands for: the chosen one, private when none was set. */
val SalaryForm.effectiveSector: EmploymentSector get() = sector ?: EmploymentSector.PRIVATE

/**
 * What the user has typed in the extra editor sheet; [editingId] is `null` for a new extra. The value is
 * [amountCents] for a fixed amount, else text whose unit depends on [amountType] (a multiple of the base salary or a
 * percent), [feeText] a percent.
 */
data class SalaryExtraForm(
    val editingId: String? = null,
    val name: String = "",
    val amountType: SalaryExtraAmountType = SalaryExtraAmountType.FIXED_CENTS,
    val amountText: String = "",
    val amountCents: Long = 0L,
    val months: List<Int> = emptyList(),
    val deductions: SalaryExtraDeductions = SalaryExtraDeductions.NONE,
    val feeText: String = "",
    val amountScope: SalaryExtraAmountScope = SalaryExtraAmountScope.TOTAL,
)

/** The open extra editor: [invalid] holds missing and malformed fields alike (the sheet flags typed ones only). */
data class SalaryExtraEditorState(
    val form: SalaryExtraForm,
    val invalid: Set<SalaryExtraField>,
    val canSave: Boolean,
    val mode: SalaryInputMode,
) {
    val isNew: Boolean get() = form.editingId == null
}

/** What the salary profile screen shows. */
sealed interface SalaryProfileUiState {
    data object Loading : SalaryProfileUiState

    /**
     * [breakdown] is `null` until the required amount is valid; [workingDays] of [month] drive the meal preview.
     * [canSave] is false while the required amount is missing, a field is invalid or a save is running.
     */
    data class Content(
        val form: SalaryForm,
        val invalid: Set<SalaryField>,
        val breakdown: SalaryBreakdown?,
        val canSave: Boolean,
        val hasSavedProfile: Boolean,
        val month: YearMonth,
        val yearSummary: SalaryYearSummary?,
        val extraEditor: SalaryExtraEditorState? = null,
        val canAddExtra: Boolean = true,
        /** The plan month the changed amounts apply from (default: the current month). */
        val effectiveFrom: YearMonth = month,
        /** Whether the "Valid from" row shows: always for a new profile, once the profile changed otherwise. */
        val showEffectiveFrom: Boolean = false,
    ) : SalaryProfileUiState
}

/** One-off outcomes of the salary screen. */
sealed interface SalaryProfileEvent {
    data object Done : SalaryProfileEvent

    data object Failed : SalaryProfileEvent

    /** A draft extra was removed from the form; the screen offers to put it back at [index]. */
    data class ExtraDeleted(
        val extra: SalaryExtra,
        val index: Int,
    ) : SalaryProfileEvent
}
