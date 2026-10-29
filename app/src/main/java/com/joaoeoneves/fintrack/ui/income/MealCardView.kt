package com.joaoeoneves.fintrack.ui.income

import com.joaoeoneves.fintrack.domain.model.MealCardBalance
import com.joaoeoneves.fintrack.domain.model.Period
import java.time.YearMonth

/**
 * What the Income tab's meal-card block shows for a month or a year: the plafond (the allowance received at the end
 * of the month before; for a year, the sum over its months), what was spent and the balance. [plafondFrom] is the
 * month the plafond was received in (`null` for a year).
 */
data class MealCardView(
    val plafondCents: Long,
    val spentCents: Long,
    val plafondFrom: YearMonth?,
) {
    val balanceCents: Long get() = plafondCents - spentCents
}

/**
 * The meal-card block of [period] from the plafond and spent figures, or `null` for a future month or year (nothing
 * is recorded there yet) and for periods that are neither a month nor a year. [today] decides what is the future.
 * [balance] comes from `mealCardMonthOf` (a month) or `mealCardYearOf` (a year, the sum of its twelve months).
 */
fun mealCardViewOf(
    period: Period,
    today: YearMonth,
    balance: MealCardBalance,
): MealCardView? =
    when (period) {
        Period.CurrentMonth -> monthView(today, today, balance)
        is Period.Month -> monthView(period.yearMonth, today, balance)
        is Period.Year ->
            MealCardView(balance.receivedCents, balance.spentCents, plafondFrom = null)
                .takeIf { period.year <= today.year }
        else -> null
    }

private fun monthView(
    month: YearMonth,
    today: YearMonth,
    balance: MealCardBalance,
): MealCardView? =
    MealCardView(balance.receivedCents, balance.spentCents, plafondFrom = month.minusMonths(1))
        .takeIf { month <= today }
