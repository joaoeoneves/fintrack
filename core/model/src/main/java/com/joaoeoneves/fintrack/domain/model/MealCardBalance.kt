package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth
import java.time.ZoneId

/** What the meal card took in and paid out over a period; [balanceCents] may be negative. */
data class MealCardBalance(
    val receivedCents: Long,
    val spentCents: Long,
) {
    val balanceCents: Long get() = receivedCents - spentCents
}

/**
 * The meal-card balance of a period: [receivedCents] is the meal line's received income in it and the spent amount
 * is the net of the [expenses] flagged [Expense.paidWithMealCard] (refunds flagged the same way reduce it). The
 * caller passes the period's expenses; unflagged ones are ignored.
 */
fun mealCardBalanceOf(
    receivedCents: Long,
    expenses: Iterable<Expense>,
): MealCardBalance = MealCardBalance(receivedCents, expenses.filter { it.paidWithMealCard }.netCents())

/** What the meal line(s) among [lines] received in [incomes] (attributed with the one rule of [lineOfIncome]). */
fun mealCardReceivedCents(
    incomes: Iterable<Income>,
    lines: List<PlannedIncome>,
): Long = incomes.filter { lineOfIncome(it, lines)?.salaryComponent == SalaryComponent.MEAL }.sumOf { it.amountCents }

/**
 * The meal card of [month]. Each month stands alone, nothing carries over: the plafond is what the meal line
 * received for month-1 (its incomes owned by month-1, see [ownerMonth], i.e. the allowance paid at the end of the
 * previous month); spent is the net of the card-flagged [expenses] dated in [month] (refunds flagged the same way
 * reduce it). Pass at least month-1's incomes and [month]'s expenses; anything else is ignored.
 */
fun mealCardMonthOf(
    month: YearMonth,
    incomes: Iterable<Income>,
    expenses: Iterable<Expense>,
    lines: List<PlannedIncome>,
    zone: ZoneId,
): MealCardBalance {
    val plafond = mealCardReceivedCents(filterOwnedInMonth(incomes, month.minusMonths(1), zone), lines)
    val spent = expenses.filter { it.paidWithMealCard && YearMonth.from(it.date.atZone(zone)) == month }
    return MealCardBalance(plafond, spent.netCents())
}

/** The meal card of the calendar [year]: the sum of its 12 [mealCardMonthOf] months (January uses December). */
fun mealCardYearOf(
    year: Int,
    incomes: Iterable<Income>,
    expenses: Iterable<Expense>,
    lines: List<PlannedIncome>,
    zone: ZoneId,
): MealCardBalance {
    val months = (1..MONTHS_IN_YEAR).map { mealCardMonthOf(YearMonth.of(year, it), incomes, expenses, lines, zone) }
    return MealCardBalance(months.sumOf { it.receivedCents }, months.sumOf { it.spentCents })
}

private const val MONTHS_IN_YEAR = 12

/** Whether the meal-card features apply: meals are paid on a card and the meal rate is above 0. */
fun SalaryProfile?.usesMealCard(): Boolean {
    val profile = this ?: return false
    return profile.mealPayment == MealPayment.CARD && (profile.mealRatePerDayCents ?: 0L) > 0L
}
