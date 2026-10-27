package com.joaoeoneves.fintrack.ui.theme

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NightModeSyncTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("night_mode_mirror", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @After
    fun tearDown() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    @Test
    fun apply_mapsEachPreferenceToNightMode() {
        val expected =
            mapOf(
                ThemePreference.SYSTEM to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                ThemePreference.LIGHT to AppCompatDelegate.MODE_NIGHT_NO,
                ThemePreference.DARK to AppCompatDelegate.MODE_NIGHT_YES,
            )
        for ((preference, mode) in expected) {
            NightModeSync.apply(context, preference)
            assertEquals("$preference", mode, AppCompatDelegate.getDefaultNightMode())
        }
    }

    @Test
    fun apply_mirrorsPreferenceName() {
        NightModeSync.apply(context, ThemePreference.LIGHT)
        assertEquals("LIGHT", prefs.getString("theme", null))
    }

    @Test
    fun restore_afterApply_roundTripsEveryPreference() {
        for (preference in ThemePreference.entries) {
            NightModeSync.apply(context, preference)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED)
            NightModeSync.restore(context)
            val expected =
                when (preference) {
                    ThemePreference.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    ThemePreference.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemePreference.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                }
            assertEquals("$preference", expected, AppCompatDelegate.getDefaultNightMode())
        }
    }

    @Test
    fun restore_withNothingStored_leavesModeUntouched() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        NightModeSync.restore(context)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        assertNull(prefs.getString("theme", null))
    }

    @Test
    fun restore_withUnknownStoredValue_leavesModeUntouched() {
        prefs.edit().putString("theme", "SEPIA").commit()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        NightModeSync.restore(context)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun apply_sameModeTwice_isStable() {
        NightModeSync.apply(context, ThemePreference.DARK)
        NightModeSync.apply(context, ThemePreference.DARK)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        assertEquals("DARK", prefs.getString("theme", null))
    }
}
