package com.joaoeoneves.fintrack.ui.dashboard

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.ui.common.LocalCurrency
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Instrumented Compose UI tests for the TalkBack accessibility support of [CategoryBreakdown]
 * (the stateless breakdown that replaced the old pie chart).
 *
 * The segmented bar is one `clearAndSetSemantics` node carrying only the total sentence
 * (`cd_pie_chart_total`), and each ranked row is its own `clearAndSetSemantics` node
 * ("name, N percent, amount"). These tests confirm the merged descriptions are reachable with the
 * exact expected content *and* that the bare child text nodes (category names, amounts) are not
 * independently reachable, so TalkBack does not double-announce.
 *
 * Uses [CurrencyOption.USD] with the en-US locale pinned through [LocalConfiguration], same
 * pattern as [com.joaoeoneves.fintrack.ui.common.CurrencyFormatTest].
 *
 * The bar splits the total by category kind: SHOPPING + RECURRING + TRANSFER are spending
 * ($90.00), INVESTMENTS is saved/invested ($10.00).
 */
@RunWith(AndroidJUnit4::class)
class CategoryBreakdownAccessibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val slices =
        listOf(
            CategoryTotal(BuiltInCategories.SHOPPING, 4500),
            CategoryTotal(BuiltInCategories.RECURRING, 3000),
            CategoryTotal(BuiltInCategories.TRANSFER, 1500),
            CategoryTotal(BuiltInCategories.INVESTMENTS, 1000),
        )

    private fun setBreakdownContent(slices: List<CategoryTotal>) {
        composeTestRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.US) }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalCurrency provides CurrencyOption.USD,
            ) {
                FinTrackTheme { CategoryBreakdown(slices = slices) }
            }
        }
    }

    @Test
    fun barNode_hasContentDescription_withFormattedTotalAndSpendingSavedSplit() {
        setBreakdownContent(slices)

        composeTestRule
            .onNodeWithContentDescription(
                "Outflow breakdown. Total outflow: $100.00 (spending $90.00, saved or invested $10.00).",
            ).assertExists()
    }

    @Test
    fun breakdownRows_areReachable_asSingleMergedNodesWithRoundedPercentageAndAmount() {
        setBreakdownContent(slices)

        composeTestRule.onNodeWithContentDescription("Shopping, 45 percent, $45.00").assertExists()
        composeTestRule.onNodeWithContentDescription("Recurring, 30 percent, $30.00").assertExists()
        composeTestRule.onNodeWithContentDescription("Transfer, 15 percent, $15.00").assertExists()
        composeTestRule.onNodeWithContentDescription("Investments, 10 percent, $10.00").assertExists()
    }

    @Test
    fun oldFragmentedTextNodes_areNoLongerIndependentlyReachable() {
        // The bare category names/amounts are Text children of each row. clearAndSetSemantics on each row should have
        // subsumed all of that -- if any of these still resolve, the merge didn't actually replace
        // the children's semantics, just added a redundant description alongside them.
        setBreakdownContent(slices)

        composeTestRule.onNodeWithText("Shopping").assertDoesNotExist()
        composeTestRule.onNodeWithText("Recurring").assertDoesNotExist()
        composeTestRule.onNodeWithText("Transfer").assertDoesNotExist()
        composeTestRule.onNodeWithText("Investments").assertDoesNotExist()
        composeTestRule.onNodeWithText("$45.00").assertDoesNotExist()
    }

    @Test
    fun emptySlices_doesNotCrash_andBarStillHasSummaryDescription() {
        setBreakdownContent(emptyList())

        // Total is 0 for no slices -> "$0.00" everywhere, and no per-category clause is appended
        // so the description is exactly the total
        // sentence with no trailing ". <category>, ..." fragment.
        composeTestRule
            .onNodeWithContentDescription(
                "Outflow breakdown. Total outflow: $0.00 (spending $0.00, saved or invested $0.00).",
            ).assertExists()
    }
}
