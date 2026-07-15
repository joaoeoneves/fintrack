package com.joaoeoneves.fintrack.ui.common

import androidx.compose.ui.graphics.Color
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for the plain, non-composable [categoryColorFor] resolver in CategoryDisplay.kt,
 * which backs the theme-aware [ExpenseCategory.color] composable property used by the pie chart
 * and its legend.
 */
class CategoryColorForTest {
    // ---- light mode: fixed fintech-accent brand hues ----

    @Test
    fun transfer_lightMode_resolvesToBrandBlue() {
        assertEquals(Color(0xFF2563EB), categoryColorFor(ExpenseCategory.TRANSFER, isDarkTheme = false))
    }

    @Test
    fun investments_lightMode_resolvesToBrandGreen() {
        assertEquals(Color(0xFF059669), categoryColorFor(ExpenseCategory.INVESTMENTS, isDarkTheme = false))
    }

    @Test
    fun shopping_lightMode_resolvesToBrandYellow() {
        assertEquals(Color(0xFFD97706), categoryColorFor(ExpenseCategory.SHOPPING, isDarkTheme = false))
    }

    @Test
    fun recurring_lightMode_resolvesToBrandRed() {
        assertEquals(Color(0xFFDC2626), categoryColorFor(ExpenseCategory.RECURRING, isDarkTheme = false))
    }

    // ---- dark mode: distinct tuned tones ----

    @Test
    fun transfer_darkMode_resolvesToTunedBlue() {
        assertEquals(Color(0xFF3B82F6), categoryColorFor(ExpenseCategory.TRANSFER, isDarkTheme = true))
    }

    @Test
    fun investments_darkMode_resolvesToTunedGreen() {
        assertEquals(Color(0xFF10B981), categoryColorFor(ExpenseCategory.INVESTMENTS, isDarkTheme = true))
    }

    @Test
    fun shopping_darkMode_resolvesToTunedYellow() {
        assertEquals(Color(0xFFF59E0B), categoryColorFor(ExpenseCategory.SHOPPING, isDarkTheme = true))
    }

    @Test
    fun recurring_darkMode_resolvesToTunedRed() {
        assertEquals(Color(0xFFEF4444), categoryColorFor(ExpenseCategory.RECURRING, isDarkTheme = true))
    }

    // ---- light vs. dark: every category's color must actually differ between themes ----

    @Test
    fun everyCategory_lightAndDarkColors_areDistinct() {
        for (category in ExpenseCategory.entries) {
            val light = categoryColorFor(category, isDarkTheme = false)
            val dark = categoryColorFor(category, isDarkTheme = true)
            assertNotEquals(
                "Expected $category to have distinct light/dark colors, but both were $light",
                light,
                dark,
            )
        }
    }
}
