package com.joaoeoneves.fintrack.data.backup

/** Restores a backup into the signed-in account; an interface so the restore can be faked in tests. */
interface AccountRestore {
    suspend fun restore(
        document: BackupDocument,
        onProgress: (written: Int, total: Int) -> Unit,
    ): AccountRestoreResult
}
