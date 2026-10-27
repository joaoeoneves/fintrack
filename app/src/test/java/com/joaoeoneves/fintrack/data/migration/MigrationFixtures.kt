package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalIncome
import com.joaoeoneves.fintrack.data.local.toDomainOrNull
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category

internal fun remoteSnapshot(
    expenseIds: Set<String> = emptySet(),
    incomeIds: Set<String> = emptySet(),
    budgetCategoryIds: Set<String> = emptySet(),
    categories: List<Category> = emptyList(),
) = RemoteAccountSnapshot(expenseIds, incomeIds, budgetCategoryIds, categories)

internal fun localDoc(
    expenses: List<LocalExpense> = emptyList(),
    income: List<LocalIncome> = emptyList(),
    budgets: List<LocalBudget> = emptyList(),
    categories: List<LocalCategory> = emptyList(),
    revision: Long = 1,
) = LocalDataDocument(
    revision = revision,
    expenses = expenses,
    income = income,
    budgets = budgets,
    categories = categories,
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
        categories = categories + plan.categories.mapNotNull { it.toDomainOrNull() },
    )
