package com.joaoeoneves.fintrack.ui.plan.salary

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.joaoeoneves.fintrack.domain.model.ExtraBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What an extra row shows as its amount. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en")
class ExtraAmountTextTest {
    @get:Rule
    val rule = createComposeRule()

    private fun render(breakdown: ExtraBreakdown): String {
        var text = ""
        rule.setContent { text = extraAmountText(breakdown) }
        rule.waitForIdle()
        return text
    }

    @Test
    fun singlePayment_isJustTheAmount() {
        val text = render(ExtraBreakdown("e", "Bonus", 12_300, listOf(5 to 12_300L)))

        assertFalse(text.contains("\n"))
        assertTrue(text.contains("123"))
        assertFalse(text.contains("per payment"))
    }

    @Test
    fun multiplePayments_showEachThenTheCountAndYearlyTotal() {
        val payments = listOf(1, 4, 7, 10).map { it to 24_500L }
        val text = render(ExtraBreakdown("e", "Flex", 98_000, payments))

        val (first, second) = text.split("\n")
        assertTrue(first.endsWith("per payment") && first.contains("245"))
        assertTrue(second.startsWith("4 payments") && second.contains("980"))
        assertEquals(2, text.split("\n").size)
    }
}
