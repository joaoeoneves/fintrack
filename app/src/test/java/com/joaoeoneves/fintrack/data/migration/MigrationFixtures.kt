package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalIncome
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncome
import com.joaoeoneves.fintrack.data.local.LocalRecurringItem
import com.joaoeoneves.fintrack.data.local.LocalSavingsGoal
import com.joaoeoneves.fintrack.data.local.toDomain
import com.joaoeoneves.fintrack.data.local.toDomainOrNull
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryGroup

@Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
internal fun remoteSnapshot(
    expenseIds: Set<String> = emptySet(),
    incomeIds: Set<String> = emptySet(),
    budgetCategoryIds: Set<String> = emptySet(),
    plannedIncomeIds: Set<String> = emptySet(),
    recurringIds: Set<String> = emptySet(),
    categories: List<Category> = emptyList(),
    monthSettingsIds: Set<String> = emptySet(),
    categoryGroups: List<CategoryGroup> = emptyList(),
) = RemoteAccountSnapshot(
    expenseIds,
    incomeIds,
    budgetCategoryIds,
    plannedIncomeIds,
    recurringIds,
    categories,
    monthSettingsIds,
    categoryGroups,
)

@Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
internal fun localDoc(
    expenses: List<LocalExpense> = emptyList(),
    income: List<LocalIncome> = emptyList(),
    budgets: List<LocalBudget> = emptyList(),
    categories: List<LocalCategory> = emptyList(),
    recurring: List<LocalRecurringItem> = emptyList(),
    revision: Long = 1,
) = LocalDataDocument(
    revision = revision,
    expenses = expenses,
    income = income,
    budgets = budgets,
    categories = categories,
    recurring = recurring,
)

internal fun localExpense(
    id: String,
    categoryId: String = "FOOD",
    amountCents: Long = 1000,
) = LocalExpense(id, "Item $id", amountCents, categoryId, "2026-03-10T12:00:00Z")

internal fun localIncome(
    id: String,
    amountCents: Long = 5000,
) = LocalIncome(id, "Salary", amountCents, "2026-03-01T08:00:00Z")

internal fun localPlanned(
    id: String,
    amountCents: Long = 200_000,
    label: String = "Salary $id",
    order: Int = 0,
) = LocalPlannedIncome(id, label, amountCents, order)

internal fun localCategory(
    id: String,
    name: String? = id,
    order: Int = 0,
) = LocalCategory(id, name, "home", "teal", "SPENDING", order)

/** The highest display order among the built-in categories, i.e. what an empty account reports. */
internal val BUILT_IN_MAX_ORDER: Int = BuiltInCategories.all.maxOf { it.order }

/** The account state after [plan] has been fully committed. */
internal fun RemoteAccountSnapshot.after(plan: MigrationPlan) =
    copy(
        expenseIds = expenseIds + plan.expenses.map { it.id },
        incomeIds = incomeIds + plan.income.map { it.id },
        budgetCategoryIds = budgetCategoryIds + plan.budgets.map { it.categoryId },
        plannedIncomeIds = plannedIncomeIds + plan.plannedIncome.map { it.id },
        recurringIds = recurringIds + plan.recurring.map { it.id },
        goalIds = goalIds + plan.goals.map { it.id },
        monthSettingsIds = monthSettingsIds + plan.monthSettings.map { it.month },
        categoryGroups = categoryGroups + plan.categoryGroups.map { it.toDomain() },
        categories = categories + plan.categories.mapNotNull { it.toDomainOrNull() },
    )

internal fun localRecurring(
    id: String,
    categoryId: String? = "FOOD",
    type: String = "EXPENSE",
    amountCents: Long = 5000,
) = LocalRecurringItem(
    id = id,
    type = type,
    name = "Rent $id",
    amountCents = amountCents,
    categoryId = categoryId,
    frequency = "MONTHLY",
    dayOfMonth = 15,
    startDate = "2026-01-01",
)

internal fun localGoal(
    id: String,
    categoryId: String? = null,
    recurringIds: List<String> = emptyList(),
) = LocalSavingsGoal(
    id = id,
    name = "Goal $id",
    targetCents = 120_000,
    startDate = "2026-01-01",
    linkedRecurringIds = recurringIds,
    linkedCategoryId = categoryId,
)
