package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.ui.common.LocalCurrency
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose UI tests for the TalkBack accessibility support added to [PieChart] and its
 * legend rows ([PieChartLegendRow], private but exercised via [PieChart]).
 *
 * Both the ring `Box` and each legend row `Row` now use `Modifier.clearAndSetSemantics { ... }` to
 * collapse their children into a single, purpose-built `contentDescription` instead of leaving
 * TalkBack to read fragmented child nodes (a bare "Total Spent" label plus a separately-announced
 * amount; a category name with no announced percentage or amount). These tests confirm both that
 * the new merged description is reachable with the exact expected content *and* that the old
 * fragmented text nodes are genuinely gone (i.e. `clearAndSetSemantics` subsumed the children,
 * rather than merely decorating them, which would leave the fragmented nodes still independently
 * reachable and TalkBack double-announcing).
 *
 * Uses [CurrencyOption.USD] (prefixed "$", period-decimal), same pattern as
 * [com.joaoeoneves.fintrack.ui.common.CurrencyFormatTest], for deterministic, easily-hand-computed
 * formatted-amount assertions.
 */
@RunWith(AndroidJUnit4::class)
class PieChartAccessibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val slices =
        listOf(
            CategoryTotal(ExpenseCategory.SHOPPING, 4500),
            CategoryTotal(ExpenseCategory.RECURRING, 3000),
            CategoryTotal(ExpenseCategory.TRANSFER, 1500),
            CategoryTotal(ExpenseCategory.INVESTMENTS, 1000),
        )

    private fun setPieChartContent(slices: List<CategoryTotal>) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalCurrency provides CurrencyOption.USD) {
                PieChart(slices = slices)
            }
        }
    }

    @Test
    fun ringNode_hasContentDescription_withFormattedTotal() {
        setPieChartContent(slices)

        composeTestRule
            .onNodeWithContentDescription("Total spent: $100.00.", substring = true)
            .assertExists()
    }

    @Test
    fun legendRows_areReachable_asSingleMergedNodesWithRoundedPercentageAndAmount() {
        setPieChartContent(slices)

        composeTestRule.onNodeWithContentDescription("Shopping, 45 percent, $45.00").assertExists()
        composeTestRule.onNodeWithContentDescription("Recurring, 30 percent, $30.00").assertExists()
        composeTestRule.onNodeWithContentDescription("Transfer, 15 percent, $15.00").assertExists()
        composeTestRule.onNodeWithContentDescription("Investments, 10 percent, $10.00").assertExists()
    }

    @Test
    fun oldFragmentedTextNodes_areNoLongerIndependentlyReachable() {
        // Before the fix, "Total Spent" and the bare category names/amounts were each their own
        // semantics node. clearAndSetSemantics on the ring Box and each legend Row should have
        // subsumed all of that -- if any of these still resolve, the merge didn't actually replace
        // the children's semantics, just added a redundant description alongside them.
        setPieChartContent(slices)

        composeTestRule.onNodeWithText("Total Spent").assertDoesNotExist()
        composeTestRule.onNodeWithText("Shopping").assertDoesNotExist()
        composeTestRule.onNodeWithText("Recurring").assertDoesNotExist()
        composeTestRule.onNodeWithText("Transfer").assertDoesNotExist()
        composeTestRule.onNodeWithText("Investments").assertDoesNotExist()
        composeTestRule.onNodeWithText("$45.00").assertDoesNotExist()
    }

    @Test
    fun emptySlices_doesNotCrash_andRingStillHasSummaryDescriptionWithoutPerCategoryClause() {
        setPieChartContent(emptyList())

        // Total is 0 for no slices -> "$0.00", and no per-category clause should be appended
        // (perCategoryCd.isNotEmpty() guard), so the full description is exactly the total
        // sentence with no trailing ". <category>, ..." fragment.
        composeTestRule
            .onNodeWithContentDescription("Expense breakdown. Total spent: $0.00.")
            .assertExists()
    }
}
