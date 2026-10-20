package com.joaoeoneves.fintrack.ui.common

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Instrumented Compose UI tests for [formatAmountCents]. This function is `@Composable` (it reads
 * [LocalCurrency] and the configuration's locale via [currentLocale]), so it's exercised through a
 * Compose test rule with both provided explicitly.
 *
 * Money formatting now follows the *app locale* through [java.text.NumberFormat] rather than a
 * per-currency rule: en-US renders "$1,330.40" / "-€1,330.40" (prefixed symbol, period decimal,
 * comma grouping), while pt-PT renders "-12,34 €" (comma decimal, symbol suffixed after a
 * non-breaking space). The locale is pinned per test by overriding [LocalConfiguration], so the
 * results don't depend on the device's system language.
 *
 * pt-PT output is compared after normalizing the space before the symbol: depending on the ICU
 * version the platform uses U+00A0 (NO-BREAK SPACE) or U+202F (NARROW NO-BREAK SPACE).
 */
@RunWith(AndroidJUnit4::class)
class CurrencyFormatTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun formatAmountCents_enUs_usd_positiveAmount_rendersPrefixedSymbolWithGrouping() {
        setContent(EN_US, CurrencyOption.USD, amountCents = 133_040)

        composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("$1,330.40")
    }

    @Test
    fun formatAmountCents_enUs_eur_negativeAmount_signPrecedesPrefixedSymbol() {
        setContent(EN_US, CurrencyOption.EUR, amountCents = -133_040)

        composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("-€1,330.40")
    }

    @Test
    fun formatAmountCents_enUs_eur_zeroAmount_rendersZero() {
        setContent(EN_US, CurrencyOption.EUR, amountCents = 0)

        composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("€0.00")
    }

    @Test
    fun formatAmountCents_enUs_gbp_positiveAmount_rendersPoundPrefixed() {
        setContent(EN_US, CurrencyOption.GBP, amountCents = 133_040)

        composeTestRule.onNodeWithTag(TEST_TAG).assertTextEquals("£1,330.40")
    }

    @Test
    fun formatAmountCents_ptPt_eur_negativeAmount_rendersCommaDecimalWithSuffixedSymbol() {
        setContent(PT_PT, CurrencyOption.EUR, amountCents = -1_234)

        assertEquals("-12,34 €", normalizedText())
    }

    @Test
    fun formatAmountCents_ptPt_eur_positiveAmount_rendersCommaDecimalWithSuffixedSymbol() {
        setContent(PT_PT, CurrencyOption.EUR, amountCents = 1_234)

        assertEquals("12,34 €", normalizedText())
    }

    @Test
    fun formatAmountCents_ptPt_eur_usesNonBreakingSpaceBeforeSymbol() {
        // The separator before "€" must be a non-breaking space so the amount never wraps away
        // from its symbol (and so Maestro regexes need "." rather than "\s" to match it).
        setContent(PT_PT, CurrencyOption.EUR, amountCents = 1_234)

        val separator = rawText().substringAfter("12,34").first()
        assertTrue(
            "Expected U+00A0 or U+202F before the symbol, got U+%04X".format(separator.code),
            separator.code == NBSP || separator.code == NARROW_NBSP,
        )
    }

    private fun rawText(): String =
        composeTestRule
            .onNodeWithTag(TEST_TAG)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .joinToString(separator = "") { it.text }

    private fun normalizedText(): String = rawText().replace(NBSP.toChar(), ' ').replace(NARROW_NBSP.toChar(), ' ')

    private fun setContent(
        locale: Locale,
        currency: CurrencyOption,
        amountCents: Long,
    ) {
        composeTestRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalCurrency provides currency,
            ) {
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
        const val NBSP = 0x00A0
        const val NARROW_NBSP = 0x202F
        val EN_US: Locale = Locale.US
        val PT_PT: Locale = Locale.forLanguageTag("pt-PT")
    }
}
