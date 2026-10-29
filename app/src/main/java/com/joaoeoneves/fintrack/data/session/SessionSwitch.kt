package com.joaoeoneves.fintrack.data.session

import android.util.Log
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

private const val TAG = "SessionSwitch"
private const val STOP_TIMEOUT_MS = 5_000L

/** A value tagged with the data source that produced it, so stale replays can be told apart. */
private class Tagged<R>(
    val source: DataSource,
    val value: R,
)

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
    ): Flow<R> = dataSource.flatMapLatest { source -> sourceFlow(source, fallback, block) }

    /**
     * Like [flow] but a single upstream (one Firestore listener) is shared by all collectors, kept
     * alive [STOP_TIMEOUT_MS] after the last one leaves, with replay 1 (cache dropped on stop).
     * Every value is tagged with its data source and each collector only sees values whose tag
     * matches the current source, so after a session/guest switch the replayed value of the
     * previous source is never delivered; collectors wait for the first value of the new source.
     * Call once per observed stream (store the result), not per collector.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <R> shared(
        scope: CoroutineScope,
        fallback: R,
        block: (T) -> Flow<R>,
    ): Flow<R> {
        val upstream =
            dataSource
                .flatMapLatest { source -> sourceFlow(source, fallback, block).map { Tagged(source, it) } }
                .shareIn(
                    scope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS, replayExpirationMillis = 0),
                    replay = 1,
                )
        return dataSource.flatMapLatest { source ->
            upstream.filter { it.source == source }.map { it.value }
        }
    }

    private fun <R> sourceFlow(
        source: DataSource,
        fallback: R,
        block: (T) -> Flow<R>,
    ): Flow<R> {
        val flow = block(pick(source))
        return if (source is DataSource.Local) {
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
