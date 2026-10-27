package com.joaoeoneves.fintrack.ui.common

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.joaoeoneves.fintrack.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Instrumented Compose UI tests for [ReadOnlyDateField], the shared read-only date field used by
 * both `ExpenseForm` (in `AddEditExpenseScreen.kt`) and `IncomeForm` (in `AddEditIncomeScreen.kt`).
 *
 * Regression coverage for a bug where the picker only opened when the user tapped the small
 * trailing icon. [ReadOnlyDateField] is now a single merged clickable Row (role Button) with
 * content description "Change date" and state description "<label>, <date>"; the trailing calendar
 * icon is decorative. The date text and the "Change date" description therefore resolve to the same
 * merged node, and tapping either (or anywhere on the row) opens the picker.
 *
 * Exercises [ReadOnlyDateField] directly rather than through the full `AddEditExpenseScreen`/
 * `AddEditIncomeScreen` (which need Hilt-backed ViewModels) -- this is the precise level to catch
 * an "only the icon works" regression in either form, since both delegate to this one composable.
 */
@RunWith(AndroidJUnit4::class)
class DateFieldTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // Fixed zone + instant, formatted the same way ReadOnlyDateField formats it internally (its
    // formatter is `.withZone(zone)`). The zone is passed explicitly to ReadOnlyDateField so the
    // test doesn't depend on the device's default zone. The instant is deliberately chosen so the
    // calendar day differs between UTC (Jun 15) and the field's zone (Jun 14, 20:30 PDT) -- that
    // is exactly the case the B5 "format/convert in the device's zone, not UTC" fix covers.
    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")
    private val fixedDate: Instant = Instant.parse("2024-06-15T03:30:00Z")
    private val formattedDate: String =
        DateTimeFormatter
            .ofPattern("MMM d, yyyy", Locale.getDefault(Locale.Category.FORMAT))
            .withZone(zone)
            .format(fixedDate)

    private val changeDateContentDescription: String by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.cd_change_date)
    }
    private val okButtonText: String by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.action_ok)
    }

    private fun setDateFieldContent(onDateSelected: (Instant) -> Unit = {}) {
        composeTestRule.setContent {
            ReadOnlyDateField(date = fixedDate, onDateSelected = onDateSelected, zone = zone)
        }
    }

    @Test
    fun tappingFieldTextBody_notJustTheIcon_opensDatePickerDialog() {
        setDateFieldContent()
        composeTestRule.onNodeWithText(okButtonText).assertDoesNotExist()

        // Tap the displayed date value itself, not the trailing icon -- it is part of the same
        // merged clickable node, so this must open the picker.
        composeTestRule.onNodeWithText(formattedDate).performClick()

        composeTestRule.onNodeWithText(okButtonText).assertExists()
    }

    @Test
    fun tappingTrailingIcon_stillOpensDatePickerDialog() {
        // Regression guard: the "Change date" description resolves to the merged clickable node.
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

    @Test
    fun confirmingWithoutChangingSelection_returnsOriginalInstant() {
        // B5 regression: opening the picker and pressing OK without touching the selection must
        // hand back exactly the original instant -- no day shift from a UTC/local mix-up and no
        // lost time of day.
        var selectedDate: Instant? = null
        setDateFieldContent(onDateSelected = { selectedDate = it })

        composeTestRule.onNodeWithContentDescription(changeDateContentDescription).performClick()
        composeTestRule.onNodeWithText(okButtonText).performClick()

        assertEquals(fixedDate, selectedDate)
        composeTestRule.onNodeWithText(formattedDate).assertExists()
    }
}
