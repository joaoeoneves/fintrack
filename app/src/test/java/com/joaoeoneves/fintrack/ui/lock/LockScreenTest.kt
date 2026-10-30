package com.joaoeoneves.fintrack.ui.lock

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LockScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun unlockButton_callsOnUnlock() {
        var unlocks = 0
        rule.setContent { FinTrackTheme { LockScreen(onUnlock = { unlocks++ }) } }
        rule.onNodeWithTag(TestTags.LOCK_SCREEN).assertIsDisplayed()

        rule.onNodeWithText("Unlock").performClick()

        assertEquals(1, unlocks)
    }
}
