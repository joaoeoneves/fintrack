package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.data.backup.AccountRestore
import com.joaoeoneves.fintrack.data.backup.AccountRestoreResult
import com.joaoeoneves.fintrack.data.backup.BackupDocument

/** [AccountRestore] that records its calls and answers with the queued [results] (the last one repeats). */
class FakeAccountRestore(
    vararg results: AccountRestoreResult = arrayOf(AccountRestoreResult.Success),
) : AccountRestore {
    private val queue = results.toMutableList()

    /** Every document passed to [restore], in order. */
    val calls = mutableListOf<BackupDocument>()

    /** Progress (written, total) reported before each result. */
    var progress: List<Pair<Int, Int>> = emptyList()

    /** Sets the answers for the next calls. */
    fun answer(vararg results: AccountRestoreResult) {
        queue.clear()
        queue += results
    }

    override suspend fun restore(
        document: BackupDocument,
        onProgress: (written: Int, total: Int) -> Unit,
    ): AccountRestoreResult {
        calls += document
        progress.forEach { (written, total) -> onProgress(written, total) }
        return if (queue.size > 1) queue.removeAt(0) else queue.first()
    }
}
