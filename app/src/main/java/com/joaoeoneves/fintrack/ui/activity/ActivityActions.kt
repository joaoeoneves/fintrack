package com.joaoeoneves.fintrack.ui.activity

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions

/** What the Activity tab can navigate to. */
data class ActivityCallbacks(
    val onOpenExpense: (String) -> Unit,
    val onOpenIncome: (String) -> Unit,
    val onAddExpense: (Period) -> Unit,
    val onAddIncome: (Period) -> Unit,
    val onOpenSettings: () -> Unit,
)

/** Bundles the Activity list's row-level, filter, period and add actions to keep composable parameter lists short. */
data class ActivityActions(
    val period: PeriodActions,
    val onTypeSelected: (ActivityType) -> Unit,
    val onQueryChanged: (String) -> Unit,
    val onCategorySelected: (CategoryId?) -> Unit,
    val onEnvelopeSelected: (String?) -> Unit,
    val onIncomeSourceSelected: (String?) -> Unit,
    val onSortSelected: (SortOption) -> Unit,
    val onOpen: (Transaction) -> Unit,
    val onDelete: (String) -> Unit,
    val onLongPress: (String) -> Unit,
    val onToggleSelected: (String) -> Unit,
    val onAddExpense: () -> Unit,
    val onAddIncome: () -> Unit,
)

/** What the selection bar can do with the selected expenses. */
data class SelectionBarActions(
    val onClear: () -> Unit,
    val onCategory: (Category) -> Unit,
    val onMealCard: () -> Unit,
    val onDelete: () -> Unit,
)
