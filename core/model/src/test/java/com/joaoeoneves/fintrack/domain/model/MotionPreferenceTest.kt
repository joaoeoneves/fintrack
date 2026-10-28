package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPreferenceTest {
    @Test
    fun system_followsSystemFlag() {
        assertTrue(MotionPreference.SYSTEM.resolve(systemReduced = true))
        assertFalse(MotionPreference.SYSTEM.resolve(systemReduced = false))
    }

    @Test
    fun on_alwaysReduces() {
        assertTrue(MotionPreference.ON.resolve(systemReduced = true))
        assertTrue(MotionPreference.ON.resolve(systemReduced = false))
    }

    @Test
    fun off_neverReduces() {
        assertFalse(MotionPreference.OFF.resolve(systemReduced = true))
        assertFalse(MotionPreference.OFF.resolve(systemReduced = false))
    }

    @Test
    fun userPreferences_defaultsToSystem() {
        assertEquals(MotionPreference.SYSTEM, UserPreferences().reducedMotion)
    }
}
