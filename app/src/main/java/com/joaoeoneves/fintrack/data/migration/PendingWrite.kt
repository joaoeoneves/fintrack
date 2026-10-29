package com.joaoeoneves.fintrack.data.migration

import com.google.firebase.firestore.FieldValue
import com.joaoeoneves.fintrack.data.local.LocalSavingsGoal
import com.joaoeoneves.fintrack.data.local.toDomain
import com.joaoeoneves.fintrack.data.local.toDomainOrNull
import com.joaoeoneves.fintrack.data.local.toDomainVersions
import com.joaoeoneves.fintrack.data.local.toYearMonthOrNull
import com.joaoeoneves.fintrack.data.planTimingFields
import com.joaoeoneves.fintrack.data.toFirestoreMap
import com.joaoeoneves.fintrack.domain.model.CategoryId

/** One document to create: `users/{uid}/[collection]/[id]` with [data]. */
internal class PendingWrite(
    val collection: String,
    val id: String,
    val data: Map<String, Any?>,
    /** Merge into an existing document instead of replacing it (for array unions). */
    val merge: Boolean = false,
)

/**
 * The planned writes grouped into batches, in commit order: category groups, categories, budgets, expenses, income,
 * planned income,
 * recurring items, month settings, salary profile, automation settings, goals, dismissed suggestions.
 * Every batch holds at most 400 writes; budget, expense and recurring batches also respect the rules' cap on
 * distinct custom categories. A planned record that cannot be mapped is a bug (the planner validates
 * everything) and throws instead of being silently dropped.
 */
internal fun MigrationPlan.toBatches(): List<List<PendingWrite>> {
    val groupWrites = categoryGroups.map { PendingWrite("categoryGroups", it.id, it.toDomain().toFirestoreMap()) }
    val categoryWrites =
        categories.map { category ->
            val domain = checkNotNull(category.toDomainOrNull()) { "Planned category ${category.id} is unmappable" }
            PendingWrite(
                "categories",
                category.id,
                domain.toFirestoreMap() + ("createdAt" to FieldValue.serverTimestamp()),
            )
        }
    val budgetBatches = budgetBatches()
    val expenseBatches =
        expenses.chunkedForRules { CategoryId(it.categoryId) }.map { chunk ->
            chunk.map { local ->
                val domain = checkNotNull(local.toDomainOrNull()) { "Planned expense ${local.id} is unmappable" }
                PendingWrite("expenses", local.id, domain.toFirestoreMap(includeCreatedAt = true))
            }
        }
    val incomeBatches =
        income.chunked(BATCH_CHUNK_SIZE).map { chunk ->
            chunk.map { local ->
                val domain = checkNotNull(local.toDomainOrNull()) { "Planned income ${local.id} is unmappable" }
                PendingWrite("income", local.id, domain.toFirestoreMap(includeCreatedAt = true))
            }
        }
    val plannedIncomeBatches =
        plannedIncome.chunked(BATCH_CHUNK_SIZE).map { chunk ->
            chunk.map { local -> PendingWrite("plannedIncome", local.id, local.toDomain().toFirestoreMap()) }
        }
    val recurringBatches =
        recurring.chunkedForRules { it.categoryId?.let(::CategoryId) }.map { chunk ->
            chunk.map { local ->
                val domain = checkNotNull(local.toDomainOrNull()) { "Planned recurring ${local.id} is unmappable" }
                PendingWrite("recurring", local.id, domain.toFirestoreMap())
            }
        }
    val monthSettingsBatches =
        monthSettings.chunked(BATCH_CHUNK_SIZE).map { chunk ->
            chunk.map { local ->
                val domain = checkNotNull(local.toDomainOrNull()) { "Planned month settings ${local.month} unmappable" }
                PendingWrite("monthSettings", local.month, domain.toFirestoreMap())
            }
        }
    // The salary profile is a single document in the `plan` collection, committed after the lines it produced.
    val salaryProfileBatches =
        listOfNotNull(salaryProfile).map {
            listOf(PendingWrite("plan", "salaryProfile", it.toDomain().toFirestoreMap()))
        }
    val automationBatches =
        listOfNotNull(automation).map { listOf(PendingWrite("plan", "automation", it.toDomain().toFirestoreMap())) }
    return groupWrites.chunked(BATCH_CHUNK_SIZE) + categoryWrites.chunked(BATCH_CHUNK_SIZE) + budgetBatches +
        expenseBatches + incomeBatches + plannedIncomeBatches + recurringBatches + monthSettingsBatches +
        salaryProfileBatches + automationBatches + goalBatches() + suggestionBatches()
}

// Merged into the account's list, so its own dismissals stay.
private fun MigrationPlan.suggestionBatches(): List<List<PendingWrite>> {
    if (dismissedRecurring.isEmpty()) return emptyList()
    @Suppress("SpreadOperator") // At most 500 keys, once per migration.
    val union = FieldValue.arrayUnion(*dismissedRecurring.toTypedArray())
    val data = mapOf("dismissedRecurring" to union, "updatedAt" to FieldValue.serverTimestamp())
    return listOf(listOf(PendingWrite("settings", "suggestions", data, merge = true)))
}

// Goals reference categories and recurring items by id only (shape-checked in the rules), so they go last.
private fun MigrationPlan.goalBatches(): List<List<PendingWrite>> =
    goals.chunked(BATCH_CHUNK_SIZE).map { chunk -> chunk.map { it.toPendingWrite() } }

private fun LocalSavingsGoal.toPendingWrite(): PendingWrite {
    val domain = checkNotNull(toDomainOrNull()) { "Planned goal $id is unmappable" }
    return PendingWrite("goals", id, domain.toFirestoreMap())
}

private fun MigrationPlan.budgetBatches(): List<List<PendingWrite>> =
    budgets.chunkedForRules { CategoryId(it.categoryId) }.map { chunk ->
        chunk.map {
            PendingWrite(
                "budgets",
                it.categoryId,
                mapOf(
                    "limitCents" to it.limitCents,
                    "skipMonths" to it.skipMonths.sorted(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ) + planTimingFields(it.startMonth.toYearMonthOrNull(), it.versions.toDomainVersions()),
            )
        }
    }
