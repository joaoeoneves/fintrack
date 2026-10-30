package com.joaoeoneves.fintrack.domain.repository

/** Reports when every local write has reached the server. */
interface ServerSettled {
    /**
     * Suspends until the writes made so far are acknowledged by the server, so it hangs while offline; callers
     * keep showing their last confirmed state meanwhile. A guest has no server and returns at once.
     */
    suspend fun awaitPendingWrites()
}
