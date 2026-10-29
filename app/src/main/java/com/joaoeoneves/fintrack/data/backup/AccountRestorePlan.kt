package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalBudget
import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.validOrNull
import com.joaoeoneves.fintrack.data.local.validateBudget
import com.joaoeoneves.fintrack.data.local.validateCategory
import com.joaoeoneves.fintrack.data.local.validateCategoryGroup
import com.joaoeoneves.fintrack.data.local.validateExpense
import com.joaoeoneves.fintrack.data.local.validateGoal
import com.joaoeoneves.fintrack.data.local.validateIncome
import com.joaoeoneves.fintrack.data.local.validateMonthSettings
import com.joaoeoneves.fintrack.data.local.validatePlannedIncome
import com.joaoeoneves.fintrack.data.local.validateRecurring
import com.joaoeoneves.fintrack.data.local.validateSalaryProfile
import com.joaoeoneves.fintrack.data.migration.MigrationPlan
import com.joaoeoneves.fintrack.data.migration.PendingWrite
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories

/** Most operations in one restore batch (the Firestore hard limit is 500). */
internal const val RESTORE_BATCH_SIZE = 450

/** Collections pruned by a restore, in the order they are pruned (the reverse of the upsert order). */
internal val RESTORE_PRUNE_ORDER =
    listOf(
        "goals",
        "monthSettings",
        "recurring",
        "plannedIncome",
        "income",
        "expenses",
        "budgets",
        "categories",
        "categoryGroups",
    )

private const val PLAN_COLLECTION = "plan"
private const val SALARY_PROFILE_ID = "salaryProfile"
private const val AUTOMATION_ID = "automation"

/** One document to delete: `users/{uid}/[collection]/[id]`. */
internal class PendingDelete(
    val collection: String,
    val id: String,
)

/** What a restore does to the account: [writeBatches] (upserts, in commit order) first, then [deleteBatches]. */
internal class AccountRestorePlan(
    val writeBatches: List<List<PendingWrite>>,
    val deleteBatches: List<List<PendingDelete>>,
) {
    val total: Int get() = writeBatches.sumOf { it.size } + deleteBatches.sumOf { it.size }
}

/**
 * Orders a restore: every write batch is committed first (split to at most [RESTORE_BATCH_SIZE] operations), then
 * the account documents that are not in the backup are deleted. [serverIds] holds the ids found on the server per
 * collection; [hasProfile] / [hasAutomation] say whether those singletons exist there. Nothing is deleted before
 * every upsert has been committed, so a failure part-way leaves a superset of the backup and a retry converges.
 */
internal fun accountRestorePlanOf(
    writes: List<List<PendingWrite>>,
    serverIds: Map<String, Set<String>>,
    hasProfile: Boolean,
    hasAutomation: Boolean,
): AccountRestorePlan {
    val writeBatches = writes.flatMap { it.chunked(RESTORE_BATCH_SIZE) }
    val written = writeBatches.flatten().groupBy({ it.collection }, { it.id }).mapValues { it.value.toSet() }
    val deletes = mutableListOf<PendingDelete>()
    for (collection in RESTORE_PRUNE_ORDER) {
        val keep = written[collection].orEmpty()
        serverIds[collection]
            .orEmpty()
            .filter { it !in keep }
            .sorted()
            .forEach { deletes += PendingDelete(collection, it) }
    }
    val keptPlan = written[PLAN_COLLECTION].orEmpty()
    if (hasProfile && SALARY_PROFILE_ID !in keptPlan) deletes += PendingDelete(PLAN_COLLECTION, SALARY_PROFILE_ID)
    if (hasAutomation && AUTOMATION_ID !in keptPlan) deletes += PendingDelete(PLAN_COLLECTION, AUTOMATION_ID)
    return AccountRestorePlan(writeBatches, deletes.chunked(RESTORE_BATCH_SIZE))
}

private inline fun isValid(check: () -> Unit): Boolean = runCatching(check).isSuccess

/**
 * The plan that writes [local] into an empty account as it is. `planMigration` is not reused on purpose: even
 * against an empty snapshot it merges categories and envelopes of the same name and renumbers their order, which is
 * right for merging guest data but wrong for a replace. Here every id and order is kept; records that cannot be
 * written (invalid, or pointing at a category the backup lacks) are dropped and counted in `skipped`, and dangling
 * links (envelope of a category, funding line, goal category, automation cursor) are cleared instead of
 * dropping the record.
 */
internal fun restoreMigrationPlanOf(
    local: LocalDataDocument,
    placeholderName: String = PLACEHOLDER_CATEGORY_NAME,
): MigrationPlan {
    val groupIds = local.categoryGroups.filter { isValid { validateCategoryGroup(it) } }.mapTo(mutableSetOf()) { it.id }
    val restored = restoredCategories(local, groupIds, placeholderName)
    val categories = restored.categories
    val known = BuiltInCategories.all.mapTo(mutableSetOf()) { it.id.value } + categories.map { it.id }

    val budgets = local.budgets.filter { it.isValid(known) }.distinctBy { it.categoryId }
    val expenses = local.expenses.filter { isValid { validateExpense(it, known) } }.distinctBy { it.id }
    val income = local.income.filter { isValid { validateIncome(it) } }.distinctBy { it.id }
    val recurring = local.recurring.filter { isValid { validateRecurring(it, known) } }.distinctBy { it.id }
    val monthSettings = local.monthSettings.filter { isValid { validateMonthSettings(it) } }.distinctBy { it.month }
    val goals =
        local.goals
            .map { goal -> goal.copy(linkedCategoryId = goal.linkedCategoryId?.takeIf { it in known }) }
            .filter { isValid { validateGoal(it) } }
            .distinctBy { it.id }
    val profile = local.salaryProfile?.takeIf { isValid { validateSalaryProfile(it) } }
    val lines =
        local.plannedIncome
            // Managed lines without a profile to follow would be read-only forever: they become plain lines.
            .map { if (profile != null) it else it.copy(managedKey = null) }
            .filter { isValid { validatePlannedIncome(it) } }
            .distinctBy { it.id }
    val lineIds = lines.mapTo(mutableSetOf()) { it.id }
    val groups =
        local.categoryGroups
            .filter { it.id in groupIds }
            .distinctBy { it.id }
            .map { it.copy(fundedByLineId = it.fundedByLineId?.takeIf { id -> id in lineIds }) }
    val automation =
        local.automation?.validOrNull()?.let { settings ->
            settings.copy(lineThrough = settings.lineThrough.filterKeys { id -> id in lineIds })
        }
    val skipped =
        (restored.candidateCount - categories.size) + (local.budgets.size - budgets.size) +
            (local.expenses.size - expenses.size) + (local.income.size - income.size) +
            (local.recurring.size - recurring.size) + (local.monthSettings.size - monthSettings.size) +
            (local.goals.size - goals.size) + (local.plannedIncome.size - lines.size) +
            (local.categoryGroups.size - groups.size)
    return MigrationPlan(
        categories = categories,
        budgets = budgets,
        expenses = expenses,
        income = income,
        plannedIncome = lines,
        recurring = recurring,
        skipped = skipped,
        monthSettings = monthSettings,
        categoryGroups = groups,
        salaryProfile = profile,
        goals = goals,
        automation = automation,
        dismissedRecurring = local.dismissedRecurringSuggestions.dismissalsKept(),
    )
}

private class RestoredCategories(
    val categories: List<LocalCategory>,
    val candidateCount: Int,
)

// Categories to restore: placeholders for ids only referenced elsewhere, unknown groups unlinked, invalid ones and
// duplicates dropped. candidateCount is the number considered, for the skipped tally.
private fun restoredCategories(
    local: LocalDataDocument,
    groupIds: Set<String>,
    placeholderName: String,
): RestoredCategories {
    val maxOrder = local.categories.maxOfOrNull { it.order } ?: -1
    val allCategories =
        withPlaceholderCategories(local.categories, local.expenses, local.budgets, maxOrder, local.recurring)
            .namingPlaceholders(local.categories.size, placeholderName)
    val categories =
        allCategories
            .map { it.copy(groupId = it.groupId?.takeIf { id -> id in groupIds }) }
            .filter { isValid { validateCategory(it) } }
            .distinctBy { it.id }
    return RestoredCategories(categories, allCategories.size)
}

private fun LocalBudget.isValid(known: Set<String>): Boolean {
    val budget = this
    return isValid { validateBudget(budget, known) }
}
