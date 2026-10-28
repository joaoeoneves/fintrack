package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.backup.withPlaceholderCategories
import com.joaoeoneves.fintrack.data.local.LocalAutomation
import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalIncome
import com.joaoeoneves.fintrack.data.local.LocalMonthSettings
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncome
import com.joaoeoneves.fintrack.data.local.LocalRecurringItem
import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import com.joaoeoneves.fintrack.data.local.LocalSavingsGoal
import com.joaoeoneves.fintrack.data.local.validOrNull
import com.joaoeoneves.fintrack.data.local.validateBudget
import com.joaoeoneves.fintrack.data.local.validateCategory
import com.joaoeoneves.fintrack.data.local.validateCategoryGroup
import com.joaoeoneves.fintrack.data.local.validateExpense
import com.joaoeoneves.fintrack.data.local.validateIncome
import com.joaoeoneves.fintrack.data.local.validateMonthSettings
import com.joaoeoneves.fintrack.data.local.validatePlannedIncome
import com.joaoeoneves.fintrack.data.local.validateRecurring
import com.joaoeoneves.fintrack.data.local.validateSalaryProfile
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryGroup
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
    /** The account's category groups. */
    val categoryGroups: List<CategoryGroup> = emptyList(),
    /** Whether the account already has a salary profile (a singleton: the account wins). */
    val hasSalaryProfile: Boolean = false,
    /** Ids of the account's savings goals. */
    val goalIds: Set<String> = emptySet(),
    /** Whether the account already has automation settings (a singleton: the account wins). */
    val hasAutomation: Boolean = false,
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
    val categoryGroups: List<LocalCategoryGroup> = emptyList(),
    /** The guest's salary profile, only when the account has none. */
    val salaryProfile: LocalSalaryProfile? = null,
    val goals: List<LocalSavingsGoal> = emptyList(),
    /** The guest's automation settings, only when the account has none. */
    val automation: LocalAutomation? = null,
) {
    val total: Int get() =
        categories.size + budgets.size + expenses.size + income.size + plannedIncome.size + recurring.size +
            monthSettings.size + categoryGroups.size + goals.size + (if (salaryProfile != null) 1 else 0) +
            (if (automation != null) 1 else 0)
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
 * - category groups: a local id already in the account is kept as is; else a group with the same trimmed,
 *   case-insensitive name is reused (the categories are remapped to it); else it is written under its own id,
 *   ordered after the account's groups. Categories are copied with their group remapped (none when unresolved);
 * - built-in overrides and budgets: what the account already has wins;
 * - planned income: if the account has any line, the account wins and none is copied; else every valid
 *   line is copied under its own id;
 * - the salary profile is a singleton: if the account has one the guest's is not copied (and the guest's managed
 *   income lines lose their managed key); else it is copied when valid;
 * - savings goals keep their ids; ids already in the account are skipped, the linked category is remapped like an
 *   expense's (an unresolvable one is dropped, the goal stays) and the linked recurring ids are kept as they are
 *   (recurring items keep their ids); invalid ones count as skipped;
 * - the automation settings are a singleton too: copied only when the account has none and they are valid;
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
    val groups = resolveGroups(local.categoryGroups, remote)
    val resolution = resolveCategories(categories, remote, groups.idMap)
    val known = BuiltInCategories.all.mapTo(mutableSetOf()) { it.id.value } + resolution.idMap.values

    var skipped = resolution.skipped + groups.skipped
    val budgets = mutableListOf<LocalBudget>()
    val seenBudgets = remote.budgetCategoryIds.toMutableSet()
    for (budget in local.budgets) {
        val mapped = resolution.targetOf(budget.categoryId)?.let { budget.copy(categoryId = it) }
        if (mapped == null || !isValid { mapped.validated(known) }) {
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
    val salaryProfile = profileToCopy(local, remote)
    val plannedIncome =
        plannedIncomeToCopy(local.plannedIncome, remote, resolution, groups.idMap, keepManaged = salaryProfile != null)
    if (remote.plannedIncomeIds.isEmpty()) skipped += local.plannedIncome.size - plannedIncome.size
    val recurring = recurringToCopy(local.recurring, remote, resolution, known)
    skipped += local.recurring.count { it.id !in remote.recurringIds } - recurring.size
    val monthSettings = local.monthSettings.filter { it.month !in remote.monthSettingsIds }
    val validMonthSettings = monthSettings.filter { isValid { validateMonthSettings(it) } }
    skipped += monthSettings.size - validMonthSettings.size
    val goals = goalsToCopy(local.goals, remote.goalIds, resolution::targetOf)
    skipped += local.goals.count { it.id !in remote.goalIds } - goals.size
    return MigrationPlan(
        resolution.toWrite,
        budgets,
        expenses,
        income,
        plannedIncome,
        recurring,
        skipped,
        validMonthSettings,
        groups.toWrite,
        salaryProfile,
        goals,
        local.automation.takeUnless { remote.hasAutomation }?.validOrNull(),
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
    resolution: CategoryResolution,
    groupIdMap: Map<String, String>,
    keepManaged: Boolean,
): List<LocalPlannedIncome> {
    if (remote.plannedIncomeIds.isNotEmpty()) return emptyList()
    return local
        .map { it.withEarmarkRemapped(resolution, groupIdMap) }
        // Managed lines without a profile to follow would be read-only forever: they become plain lines.
        .map { if (keepManaged) it else it.copy(managedKey = null) }
        .filter { isValid { validatePlannedIncome(it) } }
}

/** The guest's salary profile when it is valid and the account has none (a singleton: the account wins). */
private fun profileToCopy(
    local: LocalDataDocument,
    remote: RemoteAccountSnapshot,
): LocalSalaryProfile? {
    val profile = local.salaryProfile ?: return null
    return profile.takeIf { !remote.hasSalaryProfile && isValid { validateSalaryProfile(it) } }
}

// An earmark follows its category / group to the account's id; one that cannot be resolved is dropped, the line stays.
private fun LocalPlannedIncome.withEarmarkRemapped(
    resolution: CategoryResolution,
    groupIdMap: Map<String, String>,
): LocalPlannedIncome {
    val id = earmarkId ?: return copy(earmarkType = null)
    val target =
        when (earmarkType) {
            "CATEGORY" -> resolution.targetOf(id)
            "GROUP" -> groupIdMap[id]
            else -> null
        }
    return if (target == null) copy(earmarkType = null, earmarkId = null) else copy(earmarkId = target)
}

private fun LocalBudget.validated(known: Set<String>) = validateBudget(categoryId, limitCents, known, skipMonths)

private inline fun isValid(check: () -> Unit): Boolean = runCatching(check).isSuccess

private class CategoryResolver(
    private val remote: RemoteAccountSnapshot,
    private val groupIdMap: Map<String, String>,
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
        val ready = category.withGroupRemapped()
        if (category.id !in remoteIds && isValid { validateCategory(ready) }) toWrite += ready
    }

    private fun LocalCategory.withGroupRemapped(): LocalCategory = copy(groupId = groupId?.let { groupIdMap[it] })

    private fun resolveCustom(category: LocalCategory) {
        val existing = category.name?.nameKey()?.let { nameToId[it] }
        val ready = category.withGroupRemapped().copy(order = nextOrder)
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
    groupIdMap: Map<String, String>,
): CategoryResolution = CategoryResolver(remote, groupIdMap).resolve(local)

private class GroupResolution(
    /** Local group id to the account group id it ends up as. */
    val idMap: Map<String, String>,
    val toWrite: List<LocalCategoryGroup>,
    val skipped: Int,
)

// A local group already in the account (same id) or with the same name reuses the account's group.
private fun resolveGroups(
    local: List<LocalCategoryGroup>,
    remote: RemoteAccountSnapshot,
): GroupResolution {
    val remoteIds = remote.categoryGroups.mapTo(mutableSetOf()) { it.id }
    val nameToId = HashMap<String, String>()
    remote.categoryGroups.forEach { nameToId.putIfAbsent(it.name.nameKey(), it.id) }
    var nextOrder = (remote.categoryGroups.maxOfOrNull { it.order } ?: -1) + 1
    val idMap = mutableMapOf<String, String>()
    val toWrite = mutableListOf<LocalCategoryGroup>()
    var skipped = 0
    for (group in local.sortedWith(compareBy({ it.order }, { it.id }))) {
        val existing = nameToId[group.name.nameKey()]
        val ready = group.copy(order = nextOrder)
        when {
            group.id in remoteIds -> idMap[group.id] = group.id
            existing != null -> idMap[group.id] = existing
            isValid { validateCategoryGroup(ready) } -> {
                nextOrder++
                idMap[group.id] = group.id
                toWrite += ready
                nameToId[group.name.nameKey()] = group.id
            }
            else -> skipped++
        }
    }
    return GroupResolution(idMap, toWrite, skipped)
}
