package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.DEFAULT_CHRISTMAS_SUBSIDY_MONTH
import com.joaoeoneves.fintrack.domain.model.DEFAULT_EXTRA_PAY_MONTH
import com.joaoeoneves.fintrack.domain.model.DEFAULT_FLEX_FIRST_MONTH
import com.joaoeoneves.fintrack.domain.model.DEFAULT_HOLIDAY_SUBSIDY_MONTH
import com.joaoeoneves.fintrack.domain.model.SPREAD_HALF_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryBreakdown
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import java.time.YearMonth

/** The fields of the salary form that can hold an invalid value. */
enum class SalaryField {
    BASE,
    IRS_BASE,
    IRS_SUBSIDIES,
    SS,
    EXEMPT,
    NET_MONTHLY,
    NET_HOLIDAY,
    NET_CHRISTMAS,
    MEAL,
    FLEX_PERCENT,
    FLEX_ANNUAL,
    FLEX_FEE,
    PROFIT_SHARE,
    BONUS,
}

/**
 * What the user has typed in the salary profile screen: amounts and rates as text (a rate is a percent, the
 * profit share a multiplier of the base), the rest as choices. Blank optional fields mean "not set".
 */
data class SalaryForm(
    val mode: SalaryInputMode = SalaryInputMode.GROSS,
    val base: String = "",
    val spreadPpm: Int = SPREAD_HALF_PPM,
    val irsBase: String = "",
    val irsSubsidies: String = "",
    val ss: String = "",
    val exempt: String = "",
    val netMonthly: String = "",
    val netHoliday: String = "",
    val netChristmas: String = "",
    val meal: String = "",
    /** The (up to two) months the subsidies are paid in, oldest selection first. */
    val subsidyMonths: List<Int> = listOf(DEFAULT_HOLIDAY_SUBSIDY_MONTH, DEFAULT_CHRISTMAS_SUBSIDY_MONTH),
    val flexPercent: String = "",
    val flexAnnual: String = "",
    val flexFirstMonth: Int = DEFAULT_FLEX_FIRST_MONTH,
    val flexFee: String = "",
    val profitMultiplier: String = "",
    val profitMonths: List<Int> = listOf(DEFAULT_EXTRA_PAY_MONTH),
    val bonus: String = "",
    val bonusMonth: Int = DEFAULT_EXTRA_PAY_MONTH,
)

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
        val workingDays: Int,
    ) : SalaryProfileUiState
}

/** One-off outcomes of the salary screen. */
sealed interface SalaryProfileEvent {
    data object Done : SalaryProfileEvent

    data object Failed : SalaryProfileEvent
}
