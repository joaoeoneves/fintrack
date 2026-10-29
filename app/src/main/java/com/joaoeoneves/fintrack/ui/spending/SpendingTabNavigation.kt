package com.joaoeoneves.fintrack.ui.spending

import com.joaoeoneves.fintrack.domain.model.Period

/** What the Spending tab can open; a null group id creates a new group. */
class SpendingTabNavigation(
    val onOpenCategory: (String, Period) -> Unit,
    val onOpenCategories: () -> Unit,
    val onOpenGroup: (String?) -> Unit,
    val onOpenRecurring: () -> Unit,
)
