package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for the [CurrencyOption.symbol] and [CurrencyOption.displayNameRes] extension
 * properties in CurrencyDisplay.kt, mirroring the exhaustive per-entry mapping style of
 * [CategoryDisplayTest]. The resolved (localized) names are covered by
 * [CurrencyDisplayNameResourcesTest].
 */
class CurrencyDisplayTest {
    @Test
    fun usd_mapsToDollarSymbol() {
        assertEquals("$", CurrencyOption.USD.symbol)
    }

    @Test
    fun eur_mapsToEuroSymbol() {
        assertEquals("€", CurrencyOption.EUR.symbol)
    }

    @Test
    fun gbp_mapsToPoundSymbol() {
        assertEquals("£", CurrencyOption.GBP.symbol)
    }

    @Test
    fun jpy_mapsToYenSymbol() {
        assertEquals("¥", CurrencyOption.JPY.symbol)
    }

    @Test
    fun cad_mapsToCaDollarSymbol() {
        assertEquals("CA$", CurrencyOption.CAD.symbol)
    }

    @Test
    fun aud_mapsToADollarSymbol() {
        assertEquals("A$", CurrencyOption.AUD.symbol)
    }

    @Test
    fun usd_mapsToUsdNameResource() {
        assertEquals(R.string.currency_usd, CurrencyOption.USD.displayNameRes)
    }

    @Test
    fun eur_mapsToEurNameResource() {
        assertEquals(R.string.currency_eur, CurrencyOption.EUR.displayNameRes)
    }

    @Test
    fun gbp_mapsToGbpNameResource() {
        assertEquals(R.string.currency_gbp, CurrencyOption.GBP.displayNameRes)
    }

    @Test
    fun jpy_mapsToJpyNameResource() {
        assertEquals(R.string.currency_jpy, CurrencyOption.JPY.displayNameRes)
    }

    @Test
    fun cad_mapsToCadNameResource() {
        assertEquals(R.string.currency_cad, CurrencyOption.CAD.displayNameRes)
    }

    @Test
    fun aud_mapsToAudNameResource() {
        assertEquals(R.string.currency_aud, CurrencyOption.AUD.displayNameRes)
    }

    @Test
    fun everyCurrencyOption_hasANonBlankSymbol() {
        // Guards against the `when` becoming non-exhaustive (e.g. a currency added to the enum
        // without a corresponding branch here would throw at runtime rather than fail to compile).
        for (option in CurrencyOption.entries) {
            val symbol = option.symbol
            assertNotNull("Expected a non-null symbol for $option", symbol)
            assert(symbol.isNotBlank()) { "Expected a non-blank symbol for $option" }
        }
    }

    @Test
    fun allCurrencyOptions_mapToDistinctSymbols() {
        // Each of the 6 currencies must be visually distinguishable by symbol alone (even though
        // CAD/AUD both piggyback on "$", they're disambiguated with a "CA"/"A" prefix).
        val symbols = CurrencyOption.entries.map { it.symbol }

        assertEquals(
            "Expected each CurrencyOption to map to a distinct symbol, but found duplicates",
            CurrencyOption.entries.size,
            symbols.distinct().size,
        )
    }

    @Test
    fun allCurrencyOptions_mapToDistinctNameResources() {
        val resIds = CurrencyOption.entries.map { it.displayNameRes }

        assertEquals(
            "Expected each CurrencyOption to map to a distinct displayNameRes, but found duplicates",
            CurrencyOption.entries.size,
            resIds.distinct().size,
        )
    }
}
