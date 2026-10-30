package com.joaoeoneves.fintrack.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.joaoeoneves.fintrack.data.preferences.AppLockStore
import com.joaoeoneves.fintrack.data.security.AppLockController
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SettingsSecurityAndThemeTest {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val hint = "Asks for your fingerprint, face or screen lock when you open the app and after 1 minute away."
    private val unavailable = "Set a screen lock on this device to use this option."

    private fun lockViewModel(secure: Boolean): AppLockSettingViewModel {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "l.preferences_pb") }
        val store = AppLockStore(dataStore)
        return AppLockSettingViewModel(AppLockController(store, { secure }, { 0L }, scope), { secure })
    }

    @Test
    fun lockRow_deviceNotSecure_disabledWithUnavailableHint() {
        val vm = lockViewModel(secure = false)
        rule.setContent { FinTrackTheme { AppLockSection(viewModel = vm) } }

        rule.onNodeWithText("Lock the app").assertIsNotEnabled()
        rule.onNodeWithText(unavailable).assertExists()
    }

    @Test
    fun lockRow_deviceSecure_enabledWithNormalHint() {
        val vm = lockViewModel(secure = true)
        rule.setContent { FinTrackTheme { AppLockSection(viewModel = vm) } }

        rule.onNodeWithText("Lock the app").assertIsEnabled()
        rule.onNodeWithText(hint).assertExists()
    }

    @Test
    fun themeMenu_listsDarkLightSystemInOrder() {
        rule.setContent {
            FinTrackTheme {
                PreferencesSection(
                    uiState = SettingsUiState(themePreference = ThemePreference.DARK),
                    onThemeSelected = {},
                    onLanguageSelected = {},
                )
            }
        }
        rule.onNodeWithText("Theme").performClick()

        fun top(text: String): Dp {
            val item = rule.onNode(hasText(text) and hasAnyAncestor(isPopup()))
            return item.getUnclippedBoundsInRoot().top
        }

        assertTrue(top("Dark") < top("Light"))
        assertTrue(top("Light") < top("System"))
    }
}
