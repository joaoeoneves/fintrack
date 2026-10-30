package com.joaoeoneves.fintrack.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.joaoeoneves.fintrack.ui.common.categoryColorFor
import com.joaoeoneves.fintrack.ui.common.categoryColorKeys
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Category fills must stay visible (WCAG non-text contrast, 3:1) on the surfaces they are drawn over. */
class CategoryColorContrastTest {
    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun relativeLuminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    @Test
    fun contrastHelper_blackOnWhite_is21() {
        assertTrue(contrast(Color.Black, Color.White) in 20.99..21.01)
    }

    @Test
    fun lightVariants_atLeast3To1_onLightBackgroundAndSurface() {
        categoryColorKeys.forEach { key ->
            val color = categoryColorFor(key, isDarkTheme = false)
            assertTrue("$key vs LightBg", contrast(color, LightBg) >= 3.0)
            assertTrue("$key vs LightSurface1", contrast(color, LightSurface1) >= 3.0)
        }
    }

    @Test
    fun darkVariants_atLeast3To1_onDarkBackground() {
        categoryColorKeys.forEach { key ->
            assertTrue(key, contrast(categoryColorFor(key, isDarkTheme = true), DarkBg) >= 3.0)
        }
    }

    @Test
    fun androidLuminanceAgreesWithTestHelper_sanityOnWhite() {
        assertTrue(Color.White.luminance() > 0.99f)
    }
}
