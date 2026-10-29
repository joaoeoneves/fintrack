package com.joaoeoneves.fintrack.data.session

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

private const val BASE_DELAY_MS = 1_000L
private const val MAX_DELAY_MS = 60_000L
private const val MAX_SHIFT = 6

/**
 * Resubscribes to this flow after an upstream failure (e.g. a Firestore listener error), with an exponential
 * backoff capped at one minute, so a long-lived observer survives a transient error instead of ending for good.
 */
fun <T> Flow<T>.resubscribeOnError(tag: String): Flow<T> =
    retryWhen { cause, attempt ->
        Log.w(tag, "Observed flow failed; resubscribing (attempt ${attempt + 1})", cause)
        delay(minOf(BASE_DELAY_MS shl minOf(attempt.toInt(), MAX_SHIFT), MAX_DELAY_MS))
        true
    }
