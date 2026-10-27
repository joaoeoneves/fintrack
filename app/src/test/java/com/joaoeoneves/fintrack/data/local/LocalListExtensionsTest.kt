package com.joaoeoneves.fintrack.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalListExtensionsTest {
    @Test
    fun upserted_replacesFirstMatchOnly() {
        assertEquals(listOf(1, 20, 2), listOf(1, 2, 2).upserted(20) { it == 2 })
    }

    @Test
    fun upserted_appendsWhenAbsent() {
        assertEquals(listOf(1, 2, 3), listOf(1, 2).upserted(3) { it == 3 })
        assertEquals(listOf(5), emptyList<Int>().upserted(5) { it == 5 })
    }

    @Test
    fun upserted_replacesInPlace() {
        assertEquals(listOf("a", "B", "c"), listOf("a", "b", "c").upserted("B") { it == "b" })
    }
}
