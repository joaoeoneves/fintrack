package com.joaoeoneves.fintrack.domain.repository

/** Wipes all of the current session's user data. */
interface DataResetRepository {
    /**
     * Permanently deletes everything the current user (account or guest) has stored and resets the synced
     * preferences (theme, currency, language) to their defaults. The user stays signed in. A failure may leave the
     * data partly deleted; calling again finishes the job.
     */
    suspend fun deleteAllData(): Result<Unit>
}
