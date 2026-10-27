package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but rethrows [CancellationException] instead of capturing it, so wrapping a
 * suspend call never swallows coroutine cancellation.
 */
@Suppress("TooGenericExceptionCaught") // Intentional: mirrors runCatching's catch-all contract.
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/**
 * Runs [block] (a lenient document mapper) and returns null, logged with the document id, when it
 * throws -- e.g. a ClassCastException from a field of the wrong type -- so one malformed document
 * never fails a whole snapshot listener. Cancellation is rethrown.
 */
@Suppress("TooGenericExceptionCaught") // Intentional: Firestore getters throw ClassCastException/RuntimeException.
internal inline fun <T> DocumentSnapshot.mapOrNull(
    kind: String,
    block: () -> T?,
): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("DocumentMapping", "Dropping malformed $kind doc $id: ${e.javaClass.simpleName}: ${e.message}")
        null
    }
