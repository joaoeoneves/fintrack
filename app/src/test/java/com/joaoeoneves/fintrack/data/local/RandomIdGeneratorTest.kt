package com.joaoeoneves.fintrack.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomIdGeneratorTest {
    private val generator = RandomIdGenerator()

    @Test
    fun ids_are20AlphanumericChars() {
        repeat(1000) {
            val id = generator.newId()
            assertEquals(20, id.length)
            assertTrue(id, id.matches(Regex("[A-Za-z0-9]{20}")))
        }
    }

    @Test
    fun ids_haveNoDuplicatesOver10k() {
        val ids = List(10_000) { generator.newId() }
        assertEquals(10_000, ids.toSet().size)
    }

    @Test
    fun ids_useAllCharacterClasses() {
        val all = List(2000) { generator.newId() }.joinToString("")
        assertTrue(all.any { it.isUpperCase() })
        assertTrue(all.any { it.isLowerCase() })
        assertTrue(all.any { it.isDigit() })
    }
}
