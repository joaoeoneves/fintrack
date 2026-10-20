package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * Refund handling across [ExpenseCsvWriter] and [ExpenseCsvParser]: refunds are written with a
 * negative `amountCents` and read back as a positive amount with `isRefund` set. Split out of
 * [ExpenseCsvWriterTest]/[ExpenseCsvParserTest] to keep those classes bounded.
 */
class ExpenseCsvRefundTest {
    private val regular =
        Expense(
            id = "",
            name = "Shoes",
            amountCents = 5_000L,
            category = ExpenseCategory.SHOPPING,
            date = Instant.parse("2024-01-15T00:00:00Z"),
            note = null,
        )
    private val refund =
        regular.copy(name = "Shoes return", amountCents = 4_999L, note = "store credit", isRefund = true)

    private fun parse(csv: String): CsvParseResult =
        when (val outcome = ExpenseCsvParser.parse(csv, ZoneOffset.UTC)) {
            is CsvImportOutcome.Parsed -> outcome.result
            is CsvImportOutcome.Error -> throw AssertionError("Expected Parsed but was $outcome")
        }

    @Test
    fun write_refund_hasNegativeAmountCents_regularStaysPositive() {
        val lines = ExpenseCsvWriter.write(listOf(regular, refund)).split("\n")

        assertEquals("Shoes,5000,Shopping,2024-01-15T00:00:00Z,", lines[1])
        assertEquals("Shoes return,-4999,Shopping,2024-01-15T00:00:00Z,store credit", lines[2])
    }

    @Test
    fun roundTrip_preservesIsRefund_andPositiveAmount() {
        val result = parse(ExpenseCsvWriter.write(listOf(regular, refund)))

        assertTrue(result.failures.isEmpty())
        assertEquals(listOf(regular, refund), result.validExpenses)
        val parsedRefund = result.validExpenses[1]
        assertTrue(parsedRefund.isRefund)
        assertEquals(4_999L, parsedRefund.amountCents)
        assertFalse(result.validExpenses[0].isRefund)
    }

    @Test
    fun parse_negativeAmount_isRefundWithAbsoluteAmount() {
        val result = parse("name,amountCents,category,date,note\nReturn,-100,Shopping,2024-01-15,")

        val expense = result.validExpenses.single()
        assertEquals(100L, expense.amountCents)
        assertTrue(expense.isRefund)
        assertEquals(-100L, expense.signedAmountCents)
    }

    @Test
    fun parse_positiveAmount_isNotRefund() {
        val result = parse("name,amountCents,category,date,note\nCoffee,100,Shopping,2024-01-15,")

        assertFalse(result.validExpenses.single().isRefund)
    }

    @Test
    fun parse_zeroVariants_areAllRejectedWithZeroMessage() {
        listOf("0", " 0 ", "-0", "000").forEach { raw ->
            val result = parse("name,amountCents,category,date,note\nX,$raw,Shopping,2024-01-15,")

            assertTrue(raw, result.validExpenses.isEmpty())
            assertEquals("amountCents must not be zero: '$raw'", result.failures.single().reason)
        }
    }

    @Test
    fun parse_longMinValue_isInvalidAmount() {
        val raw = Long.MIN_VALUE.toString()

        val result = parse("name,amountCents,category,date,note\nX,$raw,Shopping,2024-01-15,")

        assertEquals("Invalid amountCents value: '$raw'", result.failures.single().reason)
    }
}
