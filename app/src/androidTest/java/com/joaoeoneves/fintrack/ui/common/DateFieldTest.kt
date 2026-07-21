package com.joaoeoneves.fintrack.ui.common

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.joaoeoneves.fintrack.R
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Instrumented Compose UI tests for [ReadOnlyDateField], the shared read-only date field used by
 * both `ExpenseForm` (in `AddEditExpenseScreen.kt`) and `IncomeForm` (in `AddEditIncomeScreen.kt`).
 *
 * Regression coverage for a bug where the field's [androidx.compose.material3.DatePickerDialog]
 * only opened when the user tapped the small trailing icon -- tapping the rest of the field body
 * (an [androidx.compose.material3.OutlinedTextField], which looks fully tappable) did nothing.
 * The fix wires a `MutableInteractionSource` + `PressInteraction.Release` listener on the field so
 * a tap anywhere in its bounds opens the picker, while the trailing icon's own `onClick` still
 * independently opens it too (no double-trigger, field stays read-only/non-dimmed).
 *
 * Exercises [ReadOnlyDateField] directly rather than through the full `AddEditExpenseScreen`/
 * `AddEditIncomeScreen` (which need Hilt-backed ViewModels) -- this is the precise level to catch
 * an "only the icon works" regression in either form, since both delegate to this one composable.
 */
@RunWith(AndroidJUnit4::class)
class DateFieldTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // Fixed instant, formatted the same way ReadOnlyDateField formats it internally (its own
    // formatter is `.withZone(ZoneOffset.UTC)`) -- renders as "Jun 15, 2024".
    private val fixedDate: Instant = Instant.parse("2024-06-15T00:00:00Z")
    private val formattedDate: String =
        DateTimeFormatter
            .ofPattern("MMM d, yyyy", Locale.getDefault(Locale.Category.FORMAT))
            .withZone(ZoneOffset.UTC)
            .format(fixedDate)

    private val changeDateContentDescription: String by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.cd_change_date)
    }
    private val okButtonText: String by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.action_ok)
    }

    private fun setDateFieldContent(onDateSelected: (Instant) -> Unit = {}) {
        composeTestRule.setContent {
            ReadOnlyDateField(date = fixedDate, onDateSelected = onDateSelected)
        }
    }

    @Test
    fun tappingFieldTextBody_notJustTheIcon_opensDatePickerDialog() {
        setDateFieldContent()
        composeTestRule.onNodeWithText(okButtonText).assertDoesNotExist()

        // Tap the displayed date value itself, not the trailing icon -- this is exactly the tap
        // that used to be a no-op before the MutableInteractionSource fix.
        composeTestRule.onNodeWithText(formattedDate).performClick()

        composeTestRule.onNodeWithText(okButtonText).assertExists()
    }

    @Test
    fun tappingTrailingIcon_stillOpensDatePickerDialog() {
        // Regression guard: confirm the icon's own onClick wasn't broken by the fix.
        setDateFieldContent()
        composeTestRule.onNodeWithText(okButtonText).assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription(changeDateContentDescription).performClick()

        composeTestRule.onNodeWithText(okButtonText).assertExists()
    }

    @Test
    fun tappingFieldTextBody_thenConfirming_invokesOnDateSelected() {
        // Confirms the body tap doesn't just open a dialog that's disconnected from the real
        // selection callback -- selecting a date and pressing OK still round-trips through
        // onDateSelected, same as if the icon had opened the dialog.
        var selectedDate: Instant? = null
        setDateFieldContent(onDateSelected = { selectedDate = it })

        composeTestRule.onNodeWithText(formattedDate).performClick()
        composeTestRule.onNodeWithText(okButtonText).performClick()

        assertNotNull(selectedDate)
    }
}
