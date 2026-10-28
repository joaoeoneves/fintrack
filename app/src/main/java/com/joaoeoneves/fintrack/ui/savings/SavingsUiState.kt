package com.joaoeoneves.fintrack.ui.savings

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.GoalProgress

/**
 * One savings plan card. [monthlyCents] and [dayOfMonth] describe the plan's active monthly item (`null` without
 * one) and [category] its category; [suggestedCents] is the monthly amount that would still reach the target (only
 * when there is no active monthly item and something is left to save).
 */
data class SavingsCardUi(
    val progress: GoalProgress,
    val category: Category?,
    val monthlyCents: Long?,
    val dayOfMonth: Int?,
    val suggestedCents: Long?,
) {
    val id: String get() = progress.goal.id
}

/**
 * The Savings tab: [savedCents] is the net outflow into SAVING-kind categories in [year] so far, [savingsRate] its
 * share of the year's income (`null` without income) and [cards] the active plans in order. Nothing shows until
 * [loaded].
 */
data class SavingsUiState(
    val loaded: Boolean = false,
    val year: Int = 0,
    val savedCents: Long = 0L,
    val savingsRate: Double? = null,
    val cards: List<SavingsCardUi> = emptyList(),
)
