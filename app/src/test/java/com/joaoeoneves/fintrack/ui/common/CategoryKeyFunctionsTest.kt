package com.joaoeoneves.fintrack.ui.common

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryKeyFunctionsTest {
    @Test
    fun colorForKey_unknownFallsBackToSlate() {
        assertEquals(Color(0xFF475569), categoryColorFor("nonsense", isDarkTheme = false))
        assertEquals(Color(0xFF64748B), categoryColorFor("nonsense", isDarkTheme = true))
        assertEquals(categoryColorFor("slate", false), categoryColorFor("", false))
    }

    @Test
    fun colorForKey_knownKeyDiffersFromFallback() {
        assertEquals(Color(0xFF2563EB), categoryColorFor("blue", isDarkTheme = false))
        assertNotEquals(categoryColorFor("slate", false), categoryColorFor("pink", false))
    }

    @Test
    fun colorKeys_nonEmptyDistinctAndResolvable() {
        assertTrue(categoryColorKeys.isNotEmpty())
        assertEquals(categoryColorKeys.size, categoryColorKeys.toSet().size)
    }

    @Test
    fun iconForKey_unknownFallsBackToHelpOutline() {
        val fallback = categoryIconFor("definitely-not-an-icon")
        assertSame(fallback, categoryIconFor(""))
    }

    @Test
    fun iconForKey_knownKeysResolveToNonFallback() {
        val fallback = categoryIconFor("definitely-not-an-icon")
        assertTrue(categoryIconKeys.isNotEmpty())
        assertTrue(categoryIconKeys.all { categoryIconFor(it) !== fallback })
        assertNotEquals(fallback, categoryIconFor("label"))
    }
}
