package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.domain.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Extra [needsLocaleUpdate] / [languageTags] cases beyond [AppLocalesTest]. */
class AppLocalesTagMatchingTest {
    @Test
    fun needsLocaleUpdate_ptPtCurrent_onlyEnglishAndSystemNeedUpdate() {
        assertTrue(needsLocaleUpdate("pt-PT", AppLanguage.ENGLISH))
        assertTrue(needsLocaleUpdate("pt-PT", AppLanguage.SYSTEM))
        assertFalse(needsLocaleUpdate("pt-PT", AppLanguage.PORTUGUESE))
    }

    @Test
    fun needsLocaleUpdate_isExactTagComparison() {
        assertTrue(needsLocaleUpdate("pt-BR", AppLanguage.PORTUGUESE))
        assertTrue(needsLocaleUpdate("pt", AppLanguage.PORTUGUESE))
        assertTrue(needsLocaleUpdate("en,pt-PT", AppLanguage.ENGLISH))
        assertTrue(needsLocaleUpdate("en-GB", AppLanguage.ENGLISH))
        assertTrue(needsLocaleUpdate("PT-pt", AppLanguage.PORTUGUESE))
    }

    @Test
    fun languageTags_areDistinct() {
        val tags = AppLanguage.entries.map { it.languageTags() }
        assertEquals(tags.size, tags.toSet().size)
    }
}
