package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalDataDocument

/**
 * Outcome of one migration attempt. [written] of [total] planned records were committed;
 * [skipped] local records could not be migrated. [failure] is `null` on full success; a
 * [MigrationOfflineException] means the account could not be reached.
 */
data class MigrationResult(
    val written: Int,
    val total: Int,
    val skipped: Int,
    val failure: Throwable? = null,
)

/** The account (or a commit to it) could not be reached in time; the device looks offline. */
class MigrationOfflineException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Copies the local guest data into the signed-in account. */
interface AccountMigrator {
    /**
     * Reads what the account already has (from the server), plans the copy and commits it in
     * batches, calling [onProgress] with (written so far, planned total) after the plan is made and
     * after every committed batch. Records already in the account are skipped, so calling this again
     * after a failure only writes what is left. Never throws except for cancellation.
     */
    suspend fun migrate(
        local: LocalDataDocument,
        onProgress: (written: Int, total: Int) -> Unit,
    ): MigrationResult
}
