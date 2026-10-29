package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * In-memory [EnvelopeRepository] for tests. When [categories] / [budgets] are given, saving and deleting an
 * envelope also files/unfiles that fake's categories and sets/clears its budgets, like the real repositories.
 * Not wired via Hilt; construct and pass it directly.
 */
class FakeEnvelopeRepository(
    initialEnvelopes: List<Envelope> = emptyList(),
    private val categories: FakeCategoryRepository? = null,
    private val budgets: FakeBudgetRepository? = null,
    var nextObserveEnvelopesError: Throwable? = null,
    var nextWriteError: Throwable? = null,
) : EnvelopeRepository {
    private val envelopesFlow = MutableStateFlow(initialEnvelopes)
    private var nextGeneratedId = 1

    /** Every saved edit, in order. */
    val savedEdits = mutableListOf<EnvelopeEdit>()

    /** The stored envelopes in insertion order. */
    val envelopes: List<Envelope> get() = envelopesFlow.value

    override fun observeEnvelopes(): Flow<List<Envelope>> {
        nextObserveEnvelopesError?.let { error -> return flow { throw error } }
        return envelopesFlow.map { list -> list.sortedWith(compareBy({ it.order }, { it.id })) }
    }

    override suspend fun saveEnvelope(edit: EnvelopeEdit): Result<String> =
        write {
            require(edit.name.isNotEmpty() && edit.name.codePointLength() <= MAX_GROUP_NAME_LENGTH) {
                "Invalid envelope name"
            }
            edit.amountCents?.let { require(it > 0) { "Invalid envelope amount" } }
            val current = envelopesFlow.value
            val existing = current.find { it.id == edit.id }
            val id = edit.id ?: "group-${nextGeneratedId++}"
            val saved =
                Envelope(
                    id = id,
                    name = edit.name,
                    order = existing?.order ?: ((current.maxOfOrNull { it.order } ?: -1) + 1),
                    amountCents = edit.amountCents,
                    fundedByLineId = edit.fundedByLineId,
                )
            val others =
                current.filter { it.id != id }.map {
                    if (edit.fundedByLineId != null && it.fundedByLineId == edit.fundedByLineId) {
                        it.copy(fundedByLineId = null)
                    } else {
                        it
                    }
                }
            envelopesFlow.value =
                if (existing != null) {
                    current.map { if (it.id == id) saved else others.first { o -> o.id == it.id } }
                } else {
                    others + saved
                }
            savedEdits += edit
            applyMembership(id, edit)
            id
        }

    private fun applyMembership(
        id: String,
        edit: EnvelopeEdit,
    ) = runBlocking {
        categories?.let { repo ->
            val catalog = repo.observeCategories().first()
            catalog.forEach { category ->
                when {
                    category.id in edit.categoryIds -> repo.setGroup(category.id, id)
                    category.groupId == id -> repo.setGroup(category.id, null)
                }
            }
        }
        budgets?.let { repo ->
            edit.limits.forEach { (categoryId, budget) ->
                if (budget == null) {
                    repo.clearBudget(categoryId)
                } else {
                    repo.setBudget(
                        categoryId,
                        budget.limitCents,
                        budget.skipMonths,
                    )
                }
            }
            if (edit.amountCents != null) edit.categoryIds.forEach { repo.clearBudget(it) }
        }
    }

    /** Test shorthand: saves a new envelope with just a name. */
    suspend fun addEnvelope(name: String): Result<String> =
        saveEnvelope(
            EnvelopeEdit(null, name, null, null, emptySet(), emptyMap()),
        )

    override suspend fun reorderEnvelopes(orderedIds: List<String>): Result<Unit> =
        write {
            val byId = envelopesFlow.value.associateBy { it.id }
            val reordered =
                orderedIds.mapIndexed { index, id ->
                    requireNotNull(byId[id]) { "Unknown envelope $id" }.copy(order = index)
                }
            envelopesFlow.value = reordered + envelopesFlow.value.filter { it.id !in orderedIds }
        }

    override suspend fun deleteEnvelope(id: String): Result<Unit> =
        write {
            envelopesFlow.value = envelopesFlow.value.filterNot { it.id == id }
            categories?.let { repo ->
                val members = runBlocking { repo.observeCategories().first() }.filter { it.groupId == id }
                budgets?.let { b -> members.forEach { runBlocking { b.clearBudget(it.id) } } }
                repo.ungroup(id)
            }
        }

    private inline fun <T> write(block: () -> T): Result<T> {
        nextWriteError?.let { error ->
            nextWriteError = null
            return Result.failure(error)
        }
        return runCatching(block)
    }
}
