package com.joaoeoneves.fintrack.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarInitialTest {
    @Test
    fun name_usesFirstLetterUppercased() {
        assertEquals("A", avatarInitial("Ada Lovelace", "zed@example.com"))
    }

    @Test
    fun lowercaseName_isUppercased() {
        assertEquals("A", avatarInitial("ada", null))
    }

    @Test
    fun accentedName_isUppercased() {
        assertEquals("É", avatarInitial("émile", null))
    }

    @Test
    fun leadingSpaces_areTrimmed() {
        assertEquals("B", avatarInitial("   bob", null))
    }

    @Test
    fun blankName_fallsBackToEmail() {
        assertEquals("Z", avatarInitial("   ", "zed@example.com"))
        assertEquals("Z", avatarInitial("", " zed@example.com"))
        assertEquals("Z", avatarInitial(null, "zed@example.com"))
    }

    @Test
    fun bothBlank_returnsNull() {
        assertNull(avatarInitial(null, null))
        assertNull(avatarInitial("", "  "))
        assertNull(avatarInitial("  ", null))
    }
}
