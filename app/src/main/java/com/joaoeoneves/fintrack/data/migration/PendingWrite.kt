package com.joaoeoneves.fintrack.data.migration

import com.google.firebase.firestore.FieldValue
import com.joaoeoneves.fintrack.data.local.toDomain
import com.joaoeoneves.fintrack.data.local.toDomainOrNull
import com.joaoeoneves.fintrack.data.toFirestoreMap
import com.joaoeoneves.fintrack.domain.model.CategoryId

/** One document to create: `users/{uid}/[collection]/[id]` with [data]. */
internal class PendingWrite(
    val collection: String,
    val id: String,
    val data: Map<String, Any?>,
)

/**
 * The planned writes grouped into batches, in commit order: categories, budgets, expenses, income, planned income,
 * recurring items, month settings.
 * Every batch holds at most 400 writes; budget, expense and recurring batches also respect the rules' cap on
 * distinct custom categories. A planned record that cannot be mapped is a bug (the planner validates
 * everything) and throws instead of being silently dropped.
 */
internal fun MigrationPlan.toBatches(): List<List<PendingWrite>> {
    val categoryWrites =
        categories.map { category ->
            val domain = checkNotNull(category.toDomainOrNull()) { "Planned category ${category.id} is unmappable" }
            PendingWrite(
                "categories",
                category.id,
                domain.toFirestoreMap() + ("createdAt" to FieldValue.serverTimestamp()),
            )
        }
    val budgetBatches =
        budgets.chunkedForRules { CategoryId(it.categoryId) }.map { chunk ->
            chunk.map {
                PendingWrite(
                    "budgets",
                    it.categoryId,
                    mapOf("limitCents" to it.limitCents, "updatedAt" to FieldValue.serverTimestamp()),
                )
            }
        }
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
    return categoryWrites.chunked(BATCH_CHUNK_SIZE) + budgetBatches + expenseBatches + incomeBatches +
        plannedIncomeBatches + recurringBatches + monthSettingsBatches
}
