package com.joaoeoneves.fintrack.ui.expense.addedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DecimalSeparatorForTest {
    private val ptPt: Locale = Locale.forLanguageTag("pt-PT")

    @Test
    fun english_usesDot() {
        assertEquals('.', decimalSeparatorFor(Locale.US))
        assertEquals('.', decimalSeparatorFor(Locale.UK))
    }

    @Test
    fun portuguese_usesComma() {
        assertEquals(',', decimalSeparatorFor(ptPt))
    }

    @Test
    fun prefillText_inEitherLocale_parsesBackToSameCents() {
        for (locale in listOf(Locale.US, ptPt)) {
            for (cents in listOf(1L, 1_250L, 123_456_789L)) {
                val text = cents.toAmountInputText(decimalSeparatorFor(locale))
                assertEquals("$locale: $text", cents, parseAmountCents(text).getOrThrow())
            }
        }
    }

    @Test
    fun zeroPrefill_inCommaLocale_isStillRejectedAsNonPositive() {
        val text = 0L.toAmountInputText(decimalSeparatorFor(ptPt))

        assertEquals("0,00", text)
        assertTrue(parseAmountCents(text).isFailure)
    }

    @Test
    fun ptPt_prefill_usesCommaSeparator() {
        assertEquals("12,50", 1_250L.toAmountInputText(decimalSeparatorFor(ptPt)))
    }
}
