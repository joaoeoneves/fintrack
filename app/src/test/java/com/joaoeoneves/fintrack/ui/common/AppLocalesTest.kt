package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.domain.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLocalesTest {
    @Test
    fun languageTags_perLanguage() {
        assertEquals("", AppLanguage.SYSTEM.languageTags())
        assertEquals("en", AppLanguage.ENGLISH.languageTags())
        assertEquals("pt-PT", AppLanguage.PORTUGUESE.languageTags())
    }

    @Test
    fun needsLocaleUpdate_sameTags_isFalse() {
        assertFalse(needsLocaleUpdate("en", AppLanguage.ENGLISH))
        assertFalse(needsLocaleUpdate("pt-PT", AppLanguage.PORTUGUESE))
        assertFalse(needsLocaleUpdate("", AppLanguage.SYSTEM))
    }

    @Test
    fun needsLocaleUpdate_differentTags_isTrue() {
        assertTrue(needsLocaleUpdate("en", AppLanguage.PORTUGUESE))
        assertTrue(needsLocaleUpdate("pt-PT", AppLanguage.ENGLISH))
        assertTrue(needsLocaleUpdate("", AppLanguage.ENGLISH))
    }

    @Test
    fun needsLocaleUpdate_systemWithExplicitOverride_isTrue() {
        assertTrue(needsLocaleUpdate("en", AppLanguage.SYSTEM))
        assertTrue(needsLocaleUpdate("pt-PT", AppLanguage.SYSTEM))
    }
}
