package com.joaoeoneves.fintrack.ui.plan.salary

import com.joaoeoneves.fintrack.domain.model.MAX_EXTRA_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryExtraDeductions
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.testutil.bonusExtra
import com.joaoeoneves.fintrack.testutil.flexExtra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SalaryExtraForm.toExtra] / [SalaryExtra.toExtraForm] and the extras' trip through the salary form. */
class SalaryExtraParsingTest {
    private val gross = SalaryInputMode.GROSS
    private val net = SalaryInputMode.NET

    @Suppress("LongParameterList") // mirrors the extra editor form
    private fun form(
        name: String = "Bonus",
        type: SalaryExtraAmountType = SalaryExtraAmountType.FIXED_CENTS,
        amount: String = "100",
        months: List<Int> = listOf(5),
        deductions: SalaryExtraDeductions = SalaryExtraDeductions.NONE,
        fee: String = "",
    ) = SalaryExtraForm(null, name, type, amount, months, deductions, fee)

    @Test
    fun fixed_parsesCents() {
        val (extra, invalid) = form(amount = "1234,56").toExtra("id1", gross)

        assertTrue(invalid.isEmpty())
        assertEquals(SalaryExtra("id1", "Bonus", SalaryExtraAmountType.FIXED_CENTS, 123_456, setOf(5)), extra)
    }

    @Test
    fun multiple_andPercent_parsePpm() {
        val multiple = form(type = SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM, amount = "1,5").toExtra("a", gross)
        val percent = form(type = SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM, amount = "7").toExtra("b", gross)

        assertEquals(1_500_000L, multiple.first?.amount)
        assertEquals(70_000L, percent.first?.amount)
    }

    @Test
    fun zeroBlankOrGarbageAmount_isFlaggedPerType() {
        SalaryExtraAmountType.entries.forEach { type ->
            listOf("", "0", "abc").forEach { text ->
                val (extra, invalid) = form(type = type, amount = text).toExtra("a", gross)
                assertNull("$type $text", extra)
                assertEquals(setOf(SalaryExtraField.AMOUNT), invalid)
            }
        }
    }

    @Test
    fun amountBounds_multipleAboveTenAndPercentAboveHundred_areInvalid() {
        val tooMuchMultiple = form(type = SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM, amount = "10.01")
        val tooMuchPercent = form(type = SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM, amount = "100.5")
        val maxMultiple = form(type = SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM, amount = "10")
        val maxPercent = form(type = SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM, amount = "100")

        assertEquals(setOf(SalaryExtraField.AMOUNT), tooMuchMultiple.toExtra("a", gross).second)
        assertEquals(setOf(SalaryExtraField.AMOUNT), tooMuchPercent.toExtra("a", gross).second)
        assertTrue(maxMultiple.toExtra("a", gross).second.isEmpty())
        assertTrue(maxPercent.toExtra("a", gross).second.isEmpty())
    }

    @Test
    fun name_isTrimmed_andBoundedByCodePoints() {
        assertEquals("Prize", form(name = "  Prize  ").toExtra("a", gross).first?.name)
        assertEquals(setOf(SalaryExtraField.NAME), form(name = "   ").toExtra("a", gross).second)
        assertTrue(form(name = "x".repeat(MAX_EXTRA_NAME_LENGTH)).toExtra("a", gross).second.isEmpty())
        assertEquals(
            setOf(SalaryExtraField.NAME),
            form(name = "x".repeat(MAX_EXTRA_NAME_LENGTH + 1)).toExtra("a", gross).second,
        )
        // 50 emoji = 50 code points but 100 chars.
        assertTrue(form(name = "😀".repeat(MAX_EXTRA_NAME_LENGTH)).toExtra("a", gross).second.isEmpty())
    }

    @Test
    fun noMonths_isNotCompleteButFlagsNoField() {
        val (extra, invalid) = form(months = emptyList()).toExtra("a", gross)

        assertNull(extra)
        assertTrue(invalid.isEmpty())
    }

    @Test
    fun fee_blankIsZero_valueIsParsed_garbageAndOverHundredAreInvalid() {
        assertEquals(0, form().toExtra("a", gross).first?.feePpm)
        assertEquals(5_000, form(fee = "0,5").toExtra("a", gross).first?.feePpm)
        assertEquals(setOf(SalaryExtraField.FEE), form(fee = "x").toExtra("a", gross).second)
        assertEquals(setOf(SalaryExtraField.FEE), form(fee = "100.5").toExtra("a", gross).second)
    }

    @Test
    fun netMode_forcesFixedNoDeductionsNoFee() {
        val typed =
            form(
                type = SalaryExtraAmountType.PERCENT_OF_ANNUAL_GROSS_PPM,
                amount = "250",
                deductions = SalaryExtraDeductions.IRS_SS,
                fee = "50",
            )

        val extra = typed.toExtra("a", net).first

        assertEquals(SalaryExtraAmountType.FIXED_CENTS, extra?.amountType)
        assertEquals(25_000L, extra?.amount)
        assertEquals(SalaryExtraDeductions.NONE, extra?.deductions)
        assertEquals(0, extra?.feePpm)
    }

    @Test
    fun grossMode_keepsDeductions() {
        assertEquals(
            SalaryExtraDeductions.IRS,
            form(deductions = SalaryExtraDeductions.IRS).toExtra("a", gross).first?.deductions,
        )
    }

    @Test
    fun toExtraForm_roundTripsThroughToExtra_forEveryType() {
        val extras =
            listOf(
                bonusExtra(cents = 123_456, deductions = SalaryExtraDeductions.IRS_SS),
                flexExtra(ppm = 70_500, months = setOf(2, 5, 8, 11), feePpm = 5_000),
                SalaryExtra("m", "Multi", SalaryExtraAmountType.MULTIPLE_OF_BASE_PPM, 1_500_000, setOf(3, 9)),
            )

        listOf(',', '.').forEach { sep ->
            extras.forEach { extra ->
                val editing = extra.toExtraForm(sep)
                assertEquals(extra.id, editing.editingId)
                assertEquals(extra to emptySet<SalaryExtraField>(), editing.toExtra(extra.id, gross))
            }
        }
    }

    @Test
    fun toExtraForm_formatsValuesForTheSeparator() {
        assertEquals("1234,56", bonusExtra(cents = 123_456).toExtraForm(',').amountText)
        assertEquals("7.05", flexExtra(ppm = 70_500).toExtraForm('.').amountText)
        assertEquals("0,5", flexExtra(feePpm = 5_000).toExtraForm(',').feeText)
        assertEquals("", flexExtra().toExtraForm('.').feeText)
    }

    @Test
    fun salaryForm_carriesExtrasBothWays_inGrossAndNet() {
        val extras = listOf(bonusExtra(), flexExtra())
        val grossForm = SalaryForm(base = "1000", extras = extras)
        val netForm = SalaryForm(mode = net, netMonthly = "1000", extras = extras)

        assertEquals(extras, grossForm.toResult().profile.extras)
        assertEquals(extras, netForm.toResult().profile.extras)
        assertEquals(
            extras,
            grossForm
                .toResult()
                .profile
                .toForm('.')
                .extras,
        )
    }
}
