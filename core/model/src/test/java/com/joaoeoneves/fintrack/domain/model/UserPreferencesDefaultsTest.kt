package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesDefaultsTest {
    @Test
    fun defaultLanguage_isSystem() {
        assertEquals(AppLanguage.SYSTEM, UserPreferences().language)
    }
}
