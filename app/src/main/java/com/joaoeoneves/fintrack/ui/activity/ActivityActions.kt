package com.joaoeoneves.fintrack.ui.activity

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
    val onOpenSpending: (Period) -> Unit = {},
    val onOpenIncomeTab: (Period) -> Unit = {},
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
    val onAddExpense: () -> Unit,
    val onAddIncome: () -> Unit,
    val onOpenSpending: () -> Unit,
    val onOpenIncomeTab: () -> Unit,
)
