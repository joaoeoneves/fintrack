package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.backup.withPlaceholderCategories
import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalIncome
import com.joaoeoneves.fintrack.data.local.LocalMonthSettings
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncome
import com.joaoeoneves.fintrack.data.local.LocalRecurringItem
import com.joaoeoneves.fintrack.data.local.validateBudget
import com.joaoeoneves.fintrack.data.local.validateCategory
import com.joaoeoneves.fintrack.data.local.validateExpense
import com.joaoeoneves.fintrack.data.local.validateIncome
import com.joaoeoneves.fintrack.data.local.validateMonthSettings
import com.joaoeoneves.fintrack.data.local.validatePlannedIncome
import com.joaoeoneves.fintrack.data.local.validateRecurring
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.effectiveName
import com.joaoeoneves.fintrack.domain.model.mergeCategories

/** What already exists in the account (read from the server): ids of records and the stored categories. */
data class RemoteAccountSnapshot(
    val expenseIds: Set<String>,
    val incomeIds: Set<String>,
    val budgetCategoryIds: Set<String>,
    val plannedIncomeIds: Set<String>,
    val recurringIds: Set<String>,
    /** Stored category documents only (built-in overrides plus user categories). */
    val categories: List<Category>,
    /** Months (`yyyy-MM`) the account already has settings for. */
    val monthSettingsIds: Set<String> = emptySet(),
)

/**
 * The records to create in the account, already remapped to the account's category ids.
 * [skipped] counts local records that cannot be migrated (unresolvable category or invalid);
 * records that already exist in the account are left out silently.
 */
data class MigrationPlan(
    val categories: List<LocalCategory>,
    val budgets: List<LocalBudget>,
    val expenses: List<LocalExpense>,
    val income: List<LocalIncome>,
    val plannedIncome: List<LocalPlannedIncome>,
    val recurring: List<LocalRecurringItem>,
    val skipped: Int,
    val monthSettings: List<LocalMonthSettings> = emptyList(),
) {
    val total: Int get() =
        categories.size + budgets.size + expenses.size + income.size + plannedIncome.size + recurring.size +
            monthSettings.size
}

private fun String.nameKey(): String = trim().lowercase()

private class CategoryResolution(
    /** Local category id to the account category id it ends up as. */
    val idMap: Map<String, String>,
    val toWrite: List<LocalCategory>,
    val skipped: Int,
) {
    /** The account category id for a local category id, or `null` when it cannot be resolved. */
    fun targetOf(id: String): String? = if (BuiltInCategories.isBuiltIn(CategoryId(id))) id else idMap[id]
}

/**
 * Decides what to copy from [local] into the account described by [remote], without touching either:
 * - custom categories: a local id already in the account is kept as is (nothing to write); else a
 *   category with the same trimmed, case-insensitive name is reused (local id remapped); else it is
 *   written under its own id, ordered after the account's categories;
 * - built-in overrides and budgets: what the account already has wins;
 * - planned income: if the account has any line, the account wins and none is copied; else every valid
 *   line is copied under its own id;
 * - month settings keep their month id; a month the account already has wins and is skipped silently;
 * - recurring items keep their ids; ids already in the account are skipped, an expense template's
 *   category is remapped like an expense's, and unresolvable or invalid ones count as skipped;
 * - expenses and income keep their ids; ids already in the account are skipped, so re-planning
 *   after a partial run only returns what is left.
 */
fun planMigration(
    local: LocalDataDocument,
    remote: RemoteAccountSnapshot,
): MigrationPlan {
    val localMaxOrder = local.categories.maxOfOrNull { it.order } ?: -1
    val categories =
        withPlaceholderCategories(local.categories, local.expenses, local.budgets, localMaxOrder, local.recurring)
    val resolution = resolveCategories(categories, remote)
    val known = BuiltInCategories.all.mapTo(mutableSetOf()) { it.id.value } + resolution.idMap.values

    var skipped = resolution.skipped
    val budgets = mutableListOf<LocalBudget>()
    val seenBudgets = remote.budgetCategoryIds.toMutableSet()
    for (budget in local.budgets) {
        val mapped = resolution.targetOf(budget.categoryId)?.let { budget.copy(categoryId = it) }
        if (mapped == null || !isValid { validateBudget(mapped.categoryId, mapped.limitCents, known) }) {
            skipped++
        } else if (seenBudgets.add(mapped.categoryId)) {
            budgets += mapped
        }
    }
    val expenses = mutableListOf<LocalExpense>()
    for (expense in local.expenses.filter { it.id !in remote.expenseIds }) {
        val mapped = resolution.targetOf(expense.categoryId)?.let { expense.copy(categoryId = it) }
        if (mapped != null && isValid { validateExpense(mapped, known) }) expenses += mapped else skipped++
    }
    val income = mutableListOf<LocalIncome>()
    for (item in local.income.filter { it.id !in remote.incomeIds }) {
        if (isValid { validateIncome(item) }) income += item else skipped++
    }
    val plannedIncome = plannedIncomeToCopy(local.plannedIncome, remote)
    if (remote.plannedIncomeIds.isEmpty()) skipped += local.plannedIncome.size - plannedIncome.size
    val recurring = recurringToCopy(local.recurring, remote, resolution, known)
    skipped += local.recurring.count { it.id !in remote.recurringIds } - recurring.size
    val monthSettings = local.monthSettings.filter { it.month !in remote.monthSettingsIds }
    val validMonthSettings = monthSettings.filter { isValid { validateMonthSettings(it) } }
    skipped += monthSettings.size - validMonthSettings.size
    return MigrationPlan(
        resolution.toWrite,
        budgets,
        expenses,
        income,
        plannedIncome,
        recurring,
        skipped,
        validMonthSettings,
    )
}

/** Valid recurring items not yet in the account, with expense categories remapped to the account's ids. */
private fun recurringToCopy(
    local: List<LocalRecurringItem>,
    remote: RemoteAccountSnapshot,
    resolution: CategoryResolution,
    known: Set<String>,
): List<LocalRecurringItem> =
    local
        .filter { it.id !in remote.recurringIds }
        .mapNotNull { item ->
            val mapped =
                if (item.categoryId == null) {
                    item
                } else {
                    resolution.targetOf(item.categoryId)?.let { item.copy(categoryId = it) }
                }
            mapped?.takeIf { isValid { validateRecurring(it, known) } }
        }

/** Valid local lines to copy; none when the account already has any line (the account wins). */
private fun plannedIncomeToCopy(
    local: List<LocalPlannedIncome>,
    remote: RemoteAccountSnapshot,
): List<LocalPlannedIncome> {
    if (remote.plannedIncomeIds.isNotEmpty()) return emptyList()
    return local.filter { isValid { validatePlannedIncome(it) } }
}

private inline fun isValid(check: () -> Unit): Boolean = runCatching(check).isSuccess

private class CategoryResolver(
    private val remote: RemoteAccountSnapshot,
) {
    private val remoteIds = remote.categories.mapTo(mutableSetOf()) { it.id.value }
    private val nameToId = HashMap<String, String>()
    private val idMap = mutableMapOf<String, String>()
    private val toWrite = mutableListOf<LocalCategory>()
    private var skipped = 0
    private var nextOrder: Int

    init {
        remote.categories
            .filterNot { it.builtIn }
            .forEach { nameToId.putIfAbsent(it.effectiveName().nameKey(), it.id.value) }
        val accountMax = mergeCategories(remote.categories).map { it.order }.filter { it != Int.MAX_VALUE }.maxOrNull()
        nextOrder = (accountMax ?: -1) + 1
    }

    fun resolve(local: List<LocalCategory>): CategoryResolution {
        local.forEach { if (BuiltInCategories.isBuiltIn(CategoryId(it.id))) resolveOverride(it) else resolveCustom(it) }
        return CategoryResolution(idMap, toWrite, skipped)
    }

    // The account's own override of a built-in wins; otherwise the local one is copied.
    private fun resolveOverride(category: LocalCategory) {
        if (category.id !in remoteIds && isValid { validateCategory(category) }) toWrite += category
    }

    private fun resolveCustom(category: LocalCategory) {
        val existing = category.name?.nameKey()?.let { nameToId[it] }
        val ready = category.copy(order = nextOrder)
        when {
            category.id in remoteIds -> idMap[category.id] = category.id
            existing != null -> idMap[category.id] = existing
            isValid { validateCategory(ready) } -> {
                nextOrder++
                idMap[category.id] = category.id
                toWrite += ready
                category.name?.let { nameToId[it.nameKey()] = category.id }
            }
            else -> skipped++
        }
    }
}

private fun resolveCategories(
    local: List<LocalCategory>,
    remote: RemoteAccountSnapshot,
): CategoryResolution = CategoryResolver(remote).resolve(local)
