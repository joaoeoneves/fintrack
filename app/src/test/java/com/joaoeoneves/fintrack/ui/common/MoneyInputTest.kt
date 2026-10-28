package com.joaoeoneves.fintrack.ui.common

import androidx.compose.ui.text.AnnotatedString
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.ui.designsystem.MoneyVisualTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyInputTest {
    private val nbsp = '\u00A0'
    private val pt = MoneySymbols(',', nbsp)
    private val en = MoneySymbols('.', ',')

    // ---- formatCentsGrouped ----

    @Test
    fun format_pt_groupsWithNbsp_andNeverUsesDot() {
        val text = formatCentsGrouped(123_456L, pt)
        assertEquals("1${nbsp}234,56", text)
        assertFalse(text.contains('.'))
    }

    @Test
    fun format_en_groupsWithComma() {
        assertEquals("1,234.56", formatCentsGrouped(123_456L, en))
    }

    @Test
    fun format_subUnitAndZero_alwaysTwoDecimals() {
        assertEquals("0,05", formatCentsGrouped(5L, pt))
        assertEquals("0,00", formatCentsGrouped(0L, pt))
        assertEquals("0.99", formatCentsGrouped(99L, en))
        assertEquals("12.00", formatCentsGrouped(1_200L, en))
    }

    @Test
    fun format_negative_isClampedToZero() {
        assertEquals("0.00", formatCentsGrouped(-50L, en))
    }

    @Test
    fun format_max_groupsEveryThreeDigits() {
        assertEquals("1,000,000,000.00", formatCentsGrouped(MAX_AMOUNT_CENTS, en))
        assertEquals("1${nbsp}000${nbsp}000${nbsp}000,00", formatCentsGrouped(MAX_AMOUNT_CENTS, pt))
    }

    // ---- applyCentsEdit ----

    @Test
    fun edit_appendDigit_isCentsTimesTenPlusDigit() {
        assertEquals(12L, applyCentsEdit(1L, "12"))
        assertEquals(1_234L, applyCentsEdit(123L, "1234"))
    }

    @Test
    fun edit_backspace_isCentsDividedByTen() {
        assertEquals(123L, applyCentsEdit(1_234L, "123"))
        assertEquals(0L, applyCentsEdit(5L, ""))
    }

    @Test
    fun edit_leadingZerosDropped_andEmptyIsZero() {
        assertEquals(5L, applyCentsEdit(0L, "005"))
        assertEquals(0L, applyCentsEdit(5L, "000"))
        assertEquals(0L, applyCentsEdit(5L, ""))
    }

    @Test
    fun edit_nonDigitsAreIgnored() {
        assertEquals(12L, applyCentsEdit(1L, "1a2"))
        assertEquals(0L, applyCentsEdit(7L, "abc"))
        assertEquals(1_234L, applyCentsEdit(0L, "1 234,"))
    }

    @Test
    fun edit_paste_stripsSeparators() {
        assertEquals(1_234L, applyCentsEdit(0L, "12.34"))
        assertEquals(1_234L, applyCentsEdit(0L, "12,34"))
    }

    @Test
    fun edit_overMax_keepsCurrent() {
        assertEquals(MAX_AMOUNT_CENTS, applyCentsEdit(10_000_000_000L, "100000000000"))
        assertEquals(10_000_000_000L, applyCentsEdit(10_000_000_000L, "100000000001"))
        assertEquals(500L, applyCentsEdit(500L, "5000", max = 1_000L))
        assertEquals(1_000L, applyCentsEdit(500L, "1000", max = 1_000L))
    }

    @Test
    fun edit_moreThanTwelveDigits_keepsCurrent() {
        assertEquals(42L, applyCentsEdit(42L, "1234567890123"))
        assertEquals(42L, applyCentsEdit(42L, "1234567890123", max = Long.MAX_VALUE))
    }

    @Test
    fun toCentsRaw_blankForZeroOrNegative() {
        assertEquals("", 0L.toCentsRaw())
        assertEquals("", (-1L).toCentsRaw())
        assertEquals("1234", 1_234L.toCentsRaw())
    }

    // ---- normalizeDecimalInput ----

    @Test
    fun normalize_ptTurnsDotIntoComma() {
        assertEquals("11,4", normalizeDecimalInput("11.4", ','))
    }

    @Test
    fun normalize_enTurnsCommaIntoDot() {
        assertEquals("11.4", normalizeDecimalInput("11,4", '.'))
    }

    @Test
    fun normalize_keepsOnlyFirstSeparator() {
        assertEquals("1,23", normalizeDecimalInput("1.2,3", ','))
        assertEquals("1.", normalizeDecimalInput("1..", '.'))
    }

    @Test
    fun normalize_dropsLettersAndSigns() {
        assertEquals("12,5", normalizeDecimalInput("a1-2b,5x", ','))
        assertEquals("", normalizeDecimalInput("abc", ','))
        assertEquals("", normalizeDecimalInput("", '.'))
    }

    // ---- moneySymbolsFor ----

    @Test
    fun symbols_english() {
        assertEquals(en, moneySymbolsFor(Locale.forLanguageTag("en")))
        assertEquals(en, moneySymbolsFor(Locale.forLanguageTag("en-US")))
    }

    @Test
    fun symbols_enGb_usesDotDecimal() {
        assertEquals(en, moneySymbolsFor(Locale.forLanguageTag("en-GB")))
    }

    @Test
    fun symbols_portugueseVariants_alwaysCommaAndNbsp() {
        for (tag in listOf("pt", "pt-PT", "pt-BR")) {
            assertEquals(tag, pt, moneySymbolsFor(Locale.forLanguageTag(tag)))
        }
    }

    @Test
    fun symbols_german_usesCommaDecimalAndDotGrouping() {
        val de = moneySymbolsFor(Locale.forLanguageTag("de-DE"))
        assertEquals(',', de.decimal)
        assertEquals('.', de.grouping)
    }

    // ---- MoneyVisualTransformation ----

    @Test
    fun transformation_showsGroupedAmount() {
        val out = MoneyVisualTransformation(en).filter(AnnotatedString("123456"))
        assertEquals("1,234.56", out.text.text)
    }

    @Test
    fun transformation_empty_showsZeroAndOffsetsStayInBounds() {
        val out = MoneyVisualTransformation(pt).filter(AnnotatedString(""))
        assertEquals("0,00", out.text.text)
        assertEquals(out.text.length, out.offsetMapping.originalToTransformed(0))
        assertEquals(0, out.offsetMapping.transformedToOriginal(out.text.length))
    }

    @Test
    fun transformation_offsetsPinnedToEnd_withinBounds() {
        val raw = "123456"
        val out = MoneyVisualTransformation(pt).filter(AnnotatedString(raw))
        for (offset in 0..raw.length) {
            val shown = out.offsetMapping.originalToTransformed(offset)
            assertTrue(shown in 0..out.text.length)
            assertEquals(out.text.length, shown)
        }
        for (offset in 0..out.text.length) {
            assertTrue(out.offsetMapping.transformedToOriginal(offset) in 0..raw.length)
        }
    }
}
