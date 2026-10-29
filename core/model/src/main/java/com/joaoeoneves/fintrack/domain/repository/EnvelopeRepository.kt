package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Envelope
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * What [EnvelopeRepository.saveEnvelope] writes: [id] is null for a new envelope. [categoryIds] is the envelope's
 * full membership; [limits] sets (or, for a null value, clears) the budgets of the listed categories.
 */
data class EnvelopeEdit(
    val id: String?,
    val name: String,
    val amountCents: Long?,
    val fundedByLineId: String?,
    val categoryIds: Set<CategoryId>,
    val limits: Map<CategoryId, Budget?>,
    /** The plan month the new amount (and a new envelope's existence) applies from. */
    val effectiveFrom: YearMonth = YearMonth.now(),
)

interface EnvelopeRepository {
    /** The envelopes sorted by order, then id. */
    fun observeEnvelopes(): Flow<List<Envelope>>

    /**
     * Creates or updates an envelope in one write; returns its id. Added categories are filed under it (moving
     * from another envelope), removed ones are unfiled and [EnvelopeEdit.limits] are applied (an empty map leaves
     * every budget alone). A new funding line is cleared on any other envelope; the amount history is kept.
     */
    suspend fun saveEnvelope(edit: EnvelopeEdit): Result<String>

    /** Persists [orderedIds]' positions as each envelope's order. */
    suspend fun reorderEnvelopes(orderedIds: List<String>): Result<Unit>

    /** Deletes an envelope and unfiles its categories (their budgets are kept), in one write. */
    suspend fun deleteEnvelope(id: String): Result<Unit>
}
