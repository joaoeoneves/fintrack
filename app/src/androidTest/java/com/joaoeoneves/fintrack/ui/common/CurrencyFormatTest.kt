package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose UI tests for [formatAmountCents]. This function is `@Composable` (it reads
 * [LocalCurrency] from composition), so it can't be exercised by a plain JVM unit test without
 * either a Compose test rule or a `CompositionLocalProvider`-based harness -- this file is that
 * harness, filling what was otherwise a real coverage gap (no existing test called
 * `formatAmountCents` directly; [CurrencyDisplayTest] only covers the non-composable
 * `decimalSeparator`/`symbolPosition`/`symbol` extension properties it delegates to).
 *
 * Covers the EUR comma-decimal/suffixed-symbol format ("1330,40€") introduced alongside
 * [CurrencyOption.decimalSeparator]/[CurrencyOption.symbolPosition], the unchanged USD
 * period-decimal/prefixed-symbol format ("$1330.40"), and the negative-amount case for both (the
 * '-' sign must survive the decimal-separator swap and stay in front of the digits, not get moved
 * or duplicated by the suffix placement).
 */
@RunWith(AndroidJUnit4::class)
class CurrencyFormatTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun formatAmountCents_eur_positiveAmount_rendersCommaDecimalWithSuffixedSymbol() {
        setContentWithCurrency(CurrencyOption.EUR, amountCents = 133_040)

        composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("1330,40€")
    }

    @Test
    fun formatAmountCents_usd_positiveAmount_rendersPeriodDecimalWithPrefixedSymbol() {
        setContentWithCurrency(CurrencyOption.USD, amountCents = 133_040)

        composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("$1330.40")
    }

    @Test
    fun formatAmountCents_eur_negativeAmount_keepsSignFirstAndSymbolSuffixed() {
        setContentWithCurrency(CurrencyOption.EUR, amountCents = -133_040)

        composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("-1330,40€")
    }

    @Test
    fun formatAmountCents_usd_negativeAmount_signPrecedesSymbol() {
        // For PREFIX currencies, the '-' sign must come before the symbol: "-$1330.40".
        setContentWithCurrency(CurrencyOption.USD, amountCents = -133_040)

        composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("-$1330.40")
    }

    @Test
    fun formatAmountCents_eur_zeroAmount_rendersZeroWithSuffixedSymbol() {
        setContentWithCurrency(CurrencyOption.EUR, amountCents = 0)

        composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("0,00€")
    }

    @Test
    fun formatAmountCents_gbp_positiveAmount_unaffectedByEurChange_stillPeriodPrefixed() {
        // GBP is one of the "everything else" currencies -- confirms the EUR-only branch didn't
        // regress a sibling PREFIX/period currency.
        setContentWithCurrency(CurrencyOption.GBP, amountCents = 133_040)

        composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("£1330.40")
    }

    private fun setContentWithCurrency(
        currency: CurrencyOption,
        amountCents: Long,
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalCurrency provides currency) {
                Column {
                    Text(
                        text = formatAmountCents(amountCents),
                        modifier = Modifier.testTag(TEST_TAG),
                    )
                }
            }
        }
    }

    private companion object {
        const val TEST_TAG = "formattedAmount"
    }
}
