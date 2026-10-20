package com.joaoeoneves.fintrack.ui.common

import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for the [CurrencyOption.symbol] and [CurrencyOption.displayName] extension
 * properties in CurrencyDisplay.kt, mirroring the exhaustive per-entry mapping style of
 * [CategoryDisplayTest].
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
    fun usd_mapsToUsDollarDisplayName() {
        assertEquals("US Dollar", CurrencyOption.USD.displayName)
    }

    @Test
    fun eur_mapsToEuroDisplayName() {
        assertEquals("Euro", CurrencyOption.EUR.displayName)
    }

    @Test
    fun gbp_mapsToBritishPoundDisplayName() {
        assertEquals("British Pound", CurrencyOption.GBP.displayName)
    }

    @Test
    fun jpy_mapsToJapaneseYenDisplayName() {
        assertEquals("Japanese Yen", CurrencyOption.JPY.displayName)
    }

    @Test
    fun cad_mapsToCanadianDollarDisplayName() {
        assertEquals("Canadian Dollar", CurrencyOption.CAD.displayName)
    }

    @Test
    fun aud_mapsToAustralianDollarDisplayName() {
        assertEquals("Australian Dollar", CurrencyOption.AUD.displayName)
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
    fun everyCurrencyOption_hasANonBlankDisplayName() {
        for (option in CurrencyOption.entries) {
            val name = option.displayName
            assertNotNull("Expected a non-null displayName for $option", name)
            assert(name.isNotBlank()) { "Expected a non-blank displayName for $option" }
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
    fun allCurrencyOptions_mapToDistinctDisplayNames() {
        val names = CurrencyOption.entries.map { it.displayName }

        assertEquals(
            "Expected each CurrencyOption to map to a distinct displayName, but found duplicates",
            CurrencyOption.entries.size,
            names.distinct().size,
        )
    }
}
