package com.joaoeoneves.fintrack.domain.repository

/** Resolves once the writes made so far are confirmed by the server (immediately for a guest). */
interface ServerSettled {
    suspend fun awaitPendingWrites()
}
