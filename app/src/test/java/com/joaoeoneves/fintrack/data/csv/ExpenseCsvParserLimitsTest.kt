package com.joaoeoneves.fintrack.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * [ExpenseCsvParser] per-row validation that mirrors firestore.rules: positive `amountCents`, and
 * name/note length limits counted in Unicode code points. Split out of [ExpenseCsvParserTest] to
 * keep that class bounded.
 */
class ExpenseCsvParserLimitsTest {
    private val header = "name,amountCents,category,date,note"

    private fun parsed(vararg rows: String): CsvParseResult {
        val csv = (listOf(header) + rows).joinToString("\n")
        return when (val outcome = ExpenseCsvParser.parse(csv, ZoneOffset.UTC)) {
            is CsvImportOutcome.Parsed -> outcome.result
            is CsvImportOutcome.Error -> throw AssertionError("Expected Parsed but was $outcome")
        }
    }

    private fun row(
        name: String = "Coffee",
        amountCents: String = "500",
        note: String = "",
    ) = "$name,$amountCents,Shopping,2024-01-15,$note"

    /** A string of exactly [count] code points, each a non-BMP emoji (2 UTF-16 chars apiece). */
    private fun emoji(count: Int) = "😀".repeat(count)

    // ---- amountCents must be positive ----

    @Test
    fun amountCents_zero_isRowFailureMentioningAmountCents() {
        val result = parsed(row(amountCents = "0"))

        assertTrue(result.validExpenses.isEmpty())
        val failure = result.failures.single()
        assertEquals(2, failure.rowNumber)
        assertTrue(failure.reason, failure.reason.contains("amountCents"))
        assertEquals("amountCents must be positive: '0'", failure.reason)
    }

    @Test
    fun amountCents_negative_isRowFailureMentioningAmountCents() {
        val result = parsed(row(amountCents = "-100"))

        assertTrue(result.validExpenses.isEmpty())
        val failure = result.failures.single()
        assertTrue(failure.reason, failure.reason.contains("amountCents"))
        assertTrue(failure.reason, failure.reason.contains("-100"))
    }

    @Test
    fun amountCents_one_isAccepted() {
        val result = parsed(row(amountCents = "1"))

        assertEquals(1L, result.validExpenses.single().amountCents)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun amountCents_withSurroundingWhitespace_isTrimmedThenValidated() {
        val result = parsed(row(amountCents = " 0 "))

        assertTrue(
            result.failures
                .single()
                .reason
                .contains("amountCents"),
        )
    }

    // ---- name length ----

    @Test
    fun name_exactly200Characters_isAccepted() {
        val name = "a".repeat(200)

        val result = parsed(row(name = name))

        assertEquals(name, result.validExpenses.single().name)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun name_201Characters_isRejected() {
        val result = parsed(row(name = "a".repeat(201)))

        assertTrue(result.validExpenses.isEmpty())
        assertEquals("Name exceeds 200 characters", result.failures.single().reason)
    }

    @Test
    fun name_200EmojiCodePoints_isAccepted_evenThough400Utf16Chars() {
        val name = emoji(200)
        assertEquals(400, name.length)

        val result = parsed(row(name = name))

        assertEquals(name, result.validExpenses.single().name)
    }

    @Test
    fun name_201EmojiCodePoints_isRejected() {
        val result = parsed(row(name = emoji(201)))

        assertEquals("Name exceeds 200 characters", result.failures.single().reason)
    }

    @Test
    fun name_200CharactersPlusSurroundingWhitespace_isMeasuredAfterTrim() {
        val name = "a".repeat(200)

        val result = parsed(row(name = "  $name  "))

        assertEquals(name, result.validExpenses.single().name)
    }

    // ---- note length ----

    @Test
    fun note_exactly2000Characters_isAccepted() {
        val note = "n".repeat(2000)

        val result = parsed(row(note = note))

        assertEquals(note, result.validExpenses.single().note)
    }

    @Test
    fun note_2001Characters_isRejected() {
        val result = parsed(row(note = "n".repeat(2001)))

        assertTrue(result.validExpenses.isEmpty())
        assertEquals("Note exceeds 2000 characters", result.failures.single().reason)
    }

    @Test
    fun note_2000EmojiCodePoints_isAccepted() {
        val result = parsed(row(note = emoji(2000)))

        assertEquals(emoji(2000), result.validExpenses.single().note)
    }

    @Test
    fun note_blank_isNullAndNotLengthChecked() {
        val result = parsed(row(note = "   "))

        assertEquals(null, result.validExpenses.single().note)
    }

    // ---- mixed file ----

    @Test
    fun mixedFile_keepsGoodRows_andReportsEachBadRowWithItsRowNumber() {
        val result =
            parsed(
                row(name = "Good1"),
                row(name = "ZeroAmount", amountCents = "0"),
                row(name = "a".repeat(201)),
                row(name = "Good2", note = "n".repeat(2000)),
                row(name = "LongNote", note = "n".repeat(2001)),
                row(name = "Negative", amountCents = "-1"),
                row(name = "Good3", amountCents = "1"),
            )

        assertEquals(listOf("Good1", "Good2", "Good3"), result.validExpenses.map { it.name })
        val reasonsByRow = result.failures.associate { it.rowNumber to it.reason }
        assertEquals(setOf(3, 4, 6, 7), reasonsByRow.keys)
        assertTrue(reasonsByRow.getValue(3).contains("amountCents"))
        assertEquals("Name exceeds 200 characters", reasonsByRow.getValue(4))
        assertEquals("Note exceeds 2000 characters", reasonsByRow.getValue(6))
        assertTrue(reasonsByRow.getValue(7).contains("amountCents"))
    }
}
