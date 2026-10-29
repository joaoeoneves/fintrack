package com.joaoeoneves.fintrack.domain.model

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

/** Whether the meal-card features apply: meals are paid on a card and the meal rate is above 0. */
fun SalaryProfile?.usesMealCard(): Boolean {
    val profile = this ?: return false
    return profile.mealPayment == MealPayment.CARD && (profile.mealRatePerDayCents ?: 0L) > 0L
}
