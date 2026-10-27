package com.joaoeoneves.fintrack.ui.common

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Resolves every [CurrencyOption.displayNameRes] through real (Robolectric-loaded) app resources,
 * in English and in pt-PT, so a missing or mistranslated `currency_*` string fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CurrencyDisplayNameResourcesTest {
    private fun names(): Map<CurrencyOption, String> {
        val resources = RuntimeEnvironment.getApplication().resources
        return CurrencyOption.entries.associateWith { resources.getString(it.displayNameRes) }
    }

    @Test
    fun everyCurrency_resolvesToExactEnglishName() {
        assertEquals(
            mapOf(
                CurrencyOption.USD to "US Dollar",
                CurrencyOption.EUR to "Euro",
                CurrencyOption.GBP to "British Pound",
                CurrencyOption.JPY to "Japanese Yen",
                CurrencyOption.CAD to "Canadian Dollar",
                CurrencyOption.AUD to "Australian Dollar",
            ),
            names(),
        )
    }

    @Test
    @Config(qualifiers = "pt-rPT")
    fun everyCurrency_resolvesToExactPortugueseName() {
        assertEquals(
            mapOf(
                CurrencyOption.USD to "Dólar americano",
                CurrencyOption.EUR to "Euro",
                CurrencyOption.GBP to "Libra esterlina",
                CurrencyOption.JPY to "Iene japonês",
                CurrencyOption.CAD to "Dólar canadiano",
                CurrencyOption.AUD to "Dólar australiano",
            ),
            names(),
        )
    }

    @Test
    fun everyCurrency_hasADistinctResolvedEnglishName() {
        assertEquals(CurrencyOption.entries.size, names().values.distinct().size)
    }
}
