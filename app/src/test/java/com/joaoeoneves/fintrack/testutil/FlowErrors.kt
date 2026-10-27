package com.joaoeoneves.fintrack.testutil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Asserts [block] fails with [expected]. Compares type and message rather than identity, because
 * kotlinx.coroutines' stack-trace recovery may rethrow a copy of the original exception.
 */
suspend fun assertFailsWithError(
    expected: Throwable,
    block: suspend () -> Unit,
) {
    val thrown = runCatching { block() }.exceptionOrNull()
    assertNotNull("expected ${expected::class.simpleName} to propagate", thrown)
    assertEquals(expected::class, thrown!!::class)
    assertEquals(expected.message, thrown.message)
}
