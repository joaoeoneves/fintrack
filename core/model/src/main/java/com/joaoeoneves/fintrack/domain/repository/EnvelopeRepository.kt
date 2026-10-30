package com.joaoeoneves.fintrack.domain.repository

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Envelope
import kotlinx.coroutines.flow.Flow

/** What [EnvelopeRepository.saveEnvelope] writes: [id] is null for a new envelope, [categoryIds] the membership. */
data class EnvelopeEdit(
    val id: String?,
    val name: String,
    val categoryIds: Set<CategoryId>,
)

interface EnvelopeRepository {
    /** The envelopes sorted by order, then id. */
    fun observeEnvelopes(): Flow<List<Envelope>>

    /**
     * Creates or updates an envelope in one write; returns its id. Added categories are filed under it (moving from
     * another envelope), removed ones are unfiled.
     */
    suspend fun saveEnvelope(edit: EnvelopeEdit): Result<String>

    /** Persists [orderedIds]' positions as each envelope's order. */
    suspend fun reorderEnvelopes(orderedIds: List<String>): Result<Unit>

    /** Deletes an envelope and unfiles its categories, in one write. */
    suspend fun deleteEnvelope(id: String): Result<Unit>
}
