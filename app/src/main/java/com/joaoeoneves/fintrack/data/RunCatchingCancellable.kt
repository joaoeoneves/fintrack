package com.joaoeoneves.fintrack.data

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
