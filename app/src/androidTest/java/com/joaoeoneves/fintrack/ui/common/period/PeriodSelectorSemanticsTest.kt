package com.joaoeoneves.fintrack.ui.common.period

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joaoeoneves.fintrack.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Semantics of [PeriodSelector]'s month arrows: the "Previous month"/"Next month" content
 * descriptions live on the clickable IconButton node itself, the same node that carries the
 * enabled/disabled state, so a content-description lookup can assert enablement directly.
 *
 * The en-US locale is pinned through [LocalConfiguration] (and a matching [LocalContext], so
 * `stringResource` resolves the English labels), same pattern as `CurrencyFormatTest`.
 */
@RunWith(AndroidJUnit4::class)
class PeriodSelectorSemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var nextClicks = 0

    private fun setSelector(canGoToNextMonth: Boolean) {
        composeTestRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.US) }
            val context = LocalContext.current.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalContext provides context,
            ) {
                PeriodSelector(
                    period = Period.CurrentMonth,
                    canGoToNextMonth = canGoToNextMonth,
                    actions = PeriodActions(onPrevious = {}, onNext = { nextClicks++ }, onSelect = {}),
                )
            }
        }
    }

    @Test
    fun nextMonthButton_isNotEnabled_whenCannotGoToNextMonth() {
        setSelector(canGoToNextMonth = false)

        composeTestRule.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Previous month").assertIsEnabled()
    }

    @Test
    fun nextMonthButton_isEnabled_andClickable_whenCanGoToNextMonth() {
        setSelector(canGoToNextMonth = true)

        composeTestRule.onNodeWithContentDescription("Next month").assertIsEnabled().performClick()

        assertEquals(1, nextClicks)
    }
}
