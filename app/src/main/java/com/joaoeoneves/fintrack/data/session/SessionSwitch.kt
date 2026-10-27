package com.joaoeoneves.fintrack.data.session

import android.util.Log
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

private const val TAG = "SessionSwitch"

/**
 * Routes calls to the [local] or the (lazily created) [remote] implementation depending on the
 * current session. Flows re-subscribe whenever the data source changes; one-shot calls wait for the
 * session to resolve first. A guest never instantiates [remote].
 */
class SessionSwitch<T>(
    sessionRepository: SessionRepository,
    private val remote: Lazy<out T>,
    private val local: T,
) {
    private val dataSource = sessionRepository.dataSource()

    private fun pick(source: DataSource): T = if (source is DataSource.Local) local else remote.get()

    /**
     * Switched flow. If the local flow fails (e.g. unreadable guest file) it is logged and replaced
     * by [fallback], like the Firestore repositories fall back to an empty result.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <R> flow(
        fallback: R,
        block: (T) -> Flow<R>,
    ): Flow<R> =
        dataSource.flatMapLatest { source ->
            val flow = block(pick(source))
            if (source is DataSource.Local) {
                flow.catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Local data flow failed; using empty result", e)
                    emit(fallback)
                }
            } else {
                flow
            }
        }

    suspend fun <R> call(block: suspend (T) -> R): R = block(pick(dataSource.first()))
}
