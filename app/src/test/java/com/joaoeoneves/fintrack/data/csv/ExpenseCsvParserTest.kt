package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for [ExpenseCsvParser].
 *
 * Bare-date (no time component) rows are resolved via `LocalDate.atStartOfDay(zone)`, where `zone`
 * defaults to `ZoneId.systemDefault()` but can be passed explicitly. Tests that care about the
 * exact resulting [Instant] for a bare date pass an explicit, non-UTC zone (matching the fixed-zone
 * convention used in `TimeRangeTest` "avoiding any dependency on the JVM's default zone") rather
 * than relying on the actual machine's default zone, which is nondeterministic across CI/machines.
 */
class ExpenseCsvParserTest {
    private val header = "name,amountCents,category,date,note"

    private fun parsed(
        csv: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CsvParseResult {
        val outcome = ExpenseCsvParser.parse(csv, zone)
        val result =
            (outcome as? CsvImportOutcome.Parsed)?.result
                ?: fail("Expected Parsed but was $outcome").let { throw IllegalStateException() }
        return result
    }

    // ---- happy path ----

    @Test
    fun validMultiRowCsv_parsesAllRowsWithCorrectFields() {
        val zone = ZoneId.of("America/New_York")
        val csv =
            """
            $header
            Coffee,500,Shopping,2024-01-15T00:00:00Z,with milk
            Rent,150000,Recurring,2024-02-01,
            """.trimIndent()

        val result = parsed(csv, zone)

        assertTrue(result.failures.isEmpty())
        assertEquals(2, result.validExpenses.size)

        val coffee = result.validExpenses[0]
        assertEquals("Coffee", coffee.name)
        assertEquals(500L, coffee.amountCents)
        assertEquals(ExpenseCategory.SHOPPING, coffee.category)
        // Full offset-aware instant: unaffected by the zone argument.
        assertEquals(Instant.parse("2024-01-15T00:00:00Z"), coffee.date)
        assertEquals("with milk", coffee.note)
        assertEquals("", coffee.id)

        val rent = result.validExpenses[1]
        assertEquals("Rent", rent.name)
        assertEquals(150_000L, rent.amountCents)
        assertEquals(ExpenseCategory.RECURRING, rent.category)
        // Bare date: resolved against the passed-in zone, not hardcoded UTC.
        assertEquals(LocalDate.parse("2024-02-01").atStartOfDay(zone).toInstant(), rent.date)
        assertNull(rent.note)
    }

    // ---- category matching ----

    @Test
    fun category_isCaseInsensitive_displayForm() {
        val csv =
            """
            $header
            A,100,shopping,2024-01-01,
            B,100,SHOPPING,2024-01-01,
            C,100,Shopping,2024-01-01,
            """.trimIndent()

        val result = parsed(csv)

        assertTrue(result.failures.isEmpty())
        assertEquals(
            listOf(ExpenseCategory.SHOPPING, ExpenseCategory.SHOPPING, ExpenseCategory.SHOPPING),
            result.validExpenses.map { it.category },
        )
    }

    @Test
    fun category_acceptsEnumNameForm() {
        val csv =
            """
            $header
            A,100,TRANSFER,2024-01-01,
            B,100,transfer,2024-01-01,
            """.trimIndent()

        val result = parsed(csv)

        assertTrue(result.failures.isEmpty())
        assertEquals(
            listOf(ExpenseCategory.TRANSFER, ExpenseCategory.TRANSFER),
            result.validExpenses.map { it.category },
        )
    }

    @Test
    fun category_acceptsAllFourValues() {
        val csv =
            """
            $header
            A,100,Transfer,2024-01-01,
            B,100,Investments,2024-01-01,
            C,100,Shopping,2024-01-01,
            D,100,Recurring,2024-01-01,
            """.trimIndent()

        val result = parsed(csv)

        assertTrue(result.failures.isEmpty())
        assertEquals(
            listOf(
                ExpenseCategory.TRANSFER,
                ExpenseCategory.INVESTMENTS,
                ExpenseCategory.SHOPPING,
                ExpenseCategory.RECURRING,
            ),
            result.validExpenses.map { it.category },
        )
    }

    // ---- date parsing ----

    @Test
    fun date_acceptsFullIso8601Instant() {
        val csv = "$header\nA,100,Shopping,2024-06-15T13:45:30Z,\n"

        val result = parsed(csv)

        assertTrue(result.failures.isEmpty())
        assertEquals(Instant.parse("2024-06-15T13:45:30Z"), result.validExpenses.single().date)
    }

    @Test
    fun date_acceptsPlainYyyyMmDd() {
        val zone = ZoneId.of("America/New_York")
        val csv = "$header\nA,100,Shopping,2024-06-15,\n"

        val result = parsed(csv, zone)

        assertTrue(result.failures.isEmpty())
        assertEquals(
            LocalDate.parse("2024-06-15").atStartOfDay(zone).toInstant(),
            result.validExpenses.single().date,
        )
    }

    // ---- bare-date zone regression (bug fix: previously always hardcoded to UTC midnight) ----

    @Test
    fun bareDate_negativeOffsetZone_landsOnWrittenCalendarDay_notOldUtcMidnightValue() {
        // America/New_York is UTC-5 in January (standard time, no DST ambiguity).
        val zone = ZoneId.of("America/New_York")
        val csv = "$header\nA,100,Shopping,2024-01-15,\n"

        val result = parsed(csv, zone)

        assertTrue(result.failures.isEmpty())
        val actual = result.validExpenses.single().date
        val expected = LocalDate.parse("2024-01-15").atStartOfDay(zone).toInstant()
        assertEquals(expected, actual)

        // The old (buggy) behavior always parsed a bare date as UTC midnight, regardless of zone.
        // Confirm we no longer produce that value for a negative-offset zone.
        val oldBuggyUtcMidnightValue = Instant.parse("2024-01-15T00:00:00Z")
        assertTrue(
            "bare date must honor the provided (negative-offset) zone, not fall back to UTC",
            actual != oldBuggyUtcMidnightValue,
        )
    }

    @Test
    fun bareDate_positiveOffsetZone_landsOnWrittenCalendarDay_notOldUtcMidnightValue() {
        val zone = ZoneId.of("Asia/Tokyo") // UTC+9, no DST.
        val csv = "$header\nA,100,Shopping,2024-01-15,\n"

        val result = parsed(csv, zone)

        assertTrue(result.failures.isEmpty())
        val actual = result.validExpenses.single().date
        val expected = LocalDate.parse("2024-01-15").atStartOfDay(zone).toInstant()
        assertEquals(expected, actual)

        val oldBuggyUtcMidnightValue = Instant.parse("2024-01-15T00:00:00Z")
        assertTrue(
            "bare date must honor the provided (positive-offset) zone, not fall back to UTC",
            actual != oldBuggyUtcMidnightValue,
        )
    }

    @Test
    fun fullInstantDate_withNonUtcZonePassedIn_remainsUnchanged_offsetAwareBranchIgnoresZone() {
        // The full-instant branch (Instant.parse) is used for the app's own CSV export format,
        // which always writes a complete offset-aware instant -- it must stay zone-independent.
        val zone = ZoneId.of("Asia/Tokyo")
        val csv = "$header\nA,100,Shopping,2024-06-15T13:45:30Z,\n"

        val result = parsed(csv, zone)

        assertTrue(result.failures.isEmpty())
        assertEquals(Instant.parse("2024-06-15T13:45:30Z"), result.validExpenses.single().date)
    }

    @Test
    fun parse_withNoExplicitZoneArgument_usesSystemDefaultZone() {
        // Smoke test documenting the production default (ZoneId.systemDefault()), the overload
        // actually used by call sites that don't pass a zone explicitly. This one test is
        // inherently tied to the machine's actual default zone, which is expected/acceptable here
        // -- every other zone-sensitive test above pins an explicit zone for determinism.
        val csv = "$header\nA,100,Shopping,2024-01-15,\n"

        val outcome = ExpenseCsvParser.parse(csv)

        val result =
            (outcome as? CsvImportOutcome.Parsed)?.result
                ?: fail("Expected Parsed but was $outcome").let { throw IllegalStateException() }
        val expected = LocalDate.parse("2024-01-15").atStartOfDay(ZoneId.systemDefault()).toInstant()
        assertEquals(expected, result.validExpenses.single().date)
    }

    // ---- note handling ----

    @Test
    fun note_blank_parsesAsNull() {
        val csv = "$header\nA,100,Shopping,2024-01-01,\n"

        val result = parsed(csv)

        assertTrue(result.failures.isEmpty())
        assertNull(result.validExpenses.single().note)
    }

    @Test
    fun note_quotedWithEmbeddedComma_parsesAsSingleField() {
        val csv = "$header\nA,100,Shopping,2024-01-01,\"lunch, with team\"\n"

        val result = parsed(csv)

        assertTrue(result.failures.isEmpty())
        assertEquals("lunch, with team", result.validExpenses.single().note)
    }

    @Test
    fun note_quotedWithEmbeddedQuote_parsesCorrectly() {
        val csv = "$header\nA,100,Shopping,2024-01-01,\"she said \"\"hi\"\"\"\n"

        val result = parsed(csv)

        assertTrue(result.failures.isEmpty())
        assertEquals("she said \"hi\"", result.validExpenses.single().note)
    }

    // ---- multi-line quoted note fields (record-splitting regression) ----

    @Test
    fun note_quotedWithEmbeddedNewline_parsesAsSingleRow_noteTextExact_secondRowIntact() {
        val zone = ZoneId.of("America/New_York")
        val csv =
            "$header\n" +
                "Coffee,500,Shopping,2024-01-15T00:00:00Z,\"line one\nline two\"\n" +
                "Rent,150000,Recurring,2024-02-01,\n"

        val result = parsed(csv, zone)

        assertTrue("expected no failures but got ${result.failures}", result.failures.isEmpty())
        assertEquals(2, result.validExpenses.size)

        val coffee = result.validExpenses[0]
        assertEquals("Coffee", coffee.name)
        assertEquals("line one\nline two", coffee.note)

        val rent = result.validExpenses[1]
        assertEquals("Rent", rent.name)
        assertEquals(150_000L, rent.amountCents)
        assertEquals(ExpenseCategory.RECURRING, rent.category)
        assertEquals(LocalDate.parse("2024-02-01").atStartOfDay(zone).toInstant(), rent.date)
        assertNull(rent.note)
    }

    @Test
    fun note_quotedWithEmbeddedCrLf_parsesAsSingleRow_noteTextExact_secondRowIntact() {
        val csv =
            "$header\n" +
                "Coffee,500,Shopping,2024-01-15T00:00:00Z,\"line one\r\nline two\"\n" +
                "Rent,150000,Recurring,2024-02-01,\n"

        val result = parsed(csv)

        assertTrue("expected no failures but got ${result.failures}", result.failures.isEmpty())
        assertEquals(2, result.validExpenses.size)

        val coffee = result.validExpenses[0]
        assertEquals("Coffee", coffee.name)
        assertEquals("line one\r\nline two", coffee.note)

        val rent = result.validExpenses[1]
        assertEquals("Rent", rent.name)
        assertEquals(150_000L, rent.amountCents)
    }

    @Test
    fun multiLineNoteRow_followedByRowWithParseError_reportsCorrectLogicalRowNumber() {
        // Row 2 (Coffee) spans two physical lines because of the embedded newline in its quoted
        // note. Row 3 (the bad-category row) must still be reported as logical row 3, not thrown
        // off by the physical line count (which would make it look like row 4).
        val csv =
            "$header\n" +
                "Coffee,500,Shopping,2024-01-15T00:00:00Z,\"line one\nline two\"\n" +
                "Bad,100,NotACategory,2024-01-01,\n"

        val result = parsed(csv)

        assertEquals(1, result.validExpenses.size)
        assertEquals("Coffee", result.validExpenses.single().name)
        assertEquals(1, result.failures.size)
        val failure = result.failures.single()
        assertEquals(3, failure.rowNumber)
        assertTrue(failure.reason.contains("category", ignoreCase = true))
    }

    @Test
    fun unterminatedQuote_malformedNote_doesNotThrow_returnsNonCrashingOutcome() {
        // An opening quote that's never closed: the rest of the file (including subsequent
        // physical lines) is swallowed into one open quoted field. parse() must return an
        // outcome, not let an exception escape.
        val csv = "$header\nA,100,Shopping,2024-01-01,\"unterminated note\n"

        val outcome = ExpenseCsvParser.parse(csv)

        // Either a fatal Error, or a Parsed result (possibly with a row failure) are both
        // acceptable non-crashing outcomes -- the only hard requirement is that parse() returns
        // normally instead of throwing.
        assertTrue(outcome is CsvImportOutcome.Error || outcome is CsvImportOutcome.Parsed)
    }

    // ---- BOM handling ----

    @Test
    fun bomPrefixedHeader_parsesSuccessfully_firstFieldNotCorrupted() {
        val bom = ""
        val csv = "$bom$header\nCoffee,500,Shopping,2024-01-15T00:00:00Z,with milk\n"

        val outcome = ExpenseCsvParser.parse(csv)

        assertTrue("expected Parsed but was $outcome", outcome is CsvImportOutcome.Parsed)
        val result = (outcome as CsvImportOutcome.Parsed).result
        assertTrue(result.failures.isEmpty())
        assertEquals(1, result.validExpenses.size)
        val coffee = result.validExpenses.single()
        assertEquals("Coffee", coffee.name)
        assertEquals(500L, coffee.amountCents)
        assertEquals(ExpenseCategory.SHOPPING, coffee.category)
        assertEquals("with milk", coffee.note)
    }

    @Test
    fun bomPrefixedFile_withActuallyWrongHeader_stillProducesError() {
        val bom = "﻿"
        val csv = "$bom" + "foo,bar,baz,qux,quux\nA,100,Shopping,2024-01-01,\n"

        val outcome = ExpenseCsvParser.parse(csv)

        assertTrue(outcome is CsvImportOutcome.Error)
    }

    @Test
    fun noBom_fileUnaffected_stillParsesNormally() {
        val csv = "$header\nCoffee,500,Shopping,2024-01-15T00:00:00Z,with milk\n"

        val result = parsed(csv)

        assertTrue(result.failures.isEmpty())
        assertEquals(1, result.validExpenses.size)
        assertEquals("Coffee", result.validExpenses.single().name)
    }

    // ---- row failures ----

    @Test
    fun badCategory_producesRowFailureMentioningCategory_otherRowsStillParse() {
        val csv =
            """
            $header
            Good,100,Shopping,2024-01-01,
            Bad,100,NotACategory,2024-01-01,
            """.trimIndent()

        val result = parsed(csv)

        assertEquals(1, result.validExpenses.size)
        assertEquals("Good", result.validExpenses.single().name)
        assertEquals(1, result.failures.size)
        val failure = result.failures.single()
        assertEquals(3, failure.rowNumber)
        assertTrue(failure.reason.contains("category", ignoreCase = true))
        assertTrue(failure.reason.contains("NotACategory"))
    }

    @Test
    fun malformedAmount_nonNumeric_producesRowFailure() {
        val csv = "$header\nA,abc,Shopping,2024-01-01,\n"

        val result = parsed(csv)

        assertTrue(result.validExpenses.isEmpty())
        assertEquals(1, result.failures.size)
        assertTrue(
            result.failures
                .single()
                .reason
                .contains("amountCents", ignoreCase = true),
        )
    }

    @Test
    fun malformedAmount_decimalValue_producesRowFailure() {
        // amountCents must be an integer number of cents; "5.00" is not a valid Long.
        val csv = "$header\nA,5.00,Shopping,2024-01-01,\n"

        val result = parsed(csv)

        assertTrue(result.validExpenses.isEmpty())
        assertEquals(1, result.failures.size)
        assertTrue(
            result.failures
                .single()
                .reason
                .contains("amountCents", ignoreCase = true),
        )
    }

    @Test
    fun malformedAmount_empty_producesRowFailure() {
        val csv = "$header\nA,,Shopping,2024-01-01,\n"

        val result = parsed(csv)

        assertTrue(result.validExpenses.isEmpty())
        assertEquals(1, result.failures.size)
        assertTrue(
            result.failures
                .single()
                .reason
                .contains("amountCents", ignoreCase = true),
        )
    }

    @Test
    fun malformedDate_garbageString_producesRowFailure() {
        val csv = "$header\nA,100,Shopping,not-a-date,\n"

        val result = parsed(csv)

        assertTrue(result.validExpenses.isEmpty())
        assertEquals(1, result.failures.size)
        val failure = result.failures.single()
        assertTrue(failure.reason.contains("date", ignoreCase = true))
        assertTrue(failure.reason.contains("not-a-date"))
    }

    @Test
    fun missingColumns_producesRowFailureMentioningColumnCount() {
        val csv = "$header\nA,100,Shopping,2024-01-01\n" // only 4 fields, missing note

        val result = parsed(csv)

        assertTrue(result.validExpenses.isEmpty())
        assertEquals(1, result.failures.size)
        val failure = result.failures.single()
        assertTrue(failure.reason.contains("5"))
        assertTrue(failure.reason.contains("4"))
    }

    @Test
    fun extraColumns_producesRowFailureMentioningColumnCount() {
        val csv = "$header\nA,100,Shopping,2024-01-01,note,extra\n" // 6 fields

        val result = parsed(csv)

        assertTrue(result.validExpenses.isEmpty())
        assertEquals(1, result.failures.size)
        val failure = result.failures.single()
        assertTrue(failure.reason.contains("5"))
        assertTrue(failure.reason.contains("6"))
    }

    @Test
    fun blankName_producesRowFailure() {
        val csv = "$header\n,100,Shopping,2024-01-01,\n"

        val result = parsed(csv)

        assertTrue(result.validExpenses.isEmpty())
        assertEquals(1, result.failures.size)
        assertTrue(
            result.failures
                .single()
                .reason
                .contains("Name", ignoreCase = true),
        )
    }

    // ---- empty / header-only / malformed header ----

    @Test
    fun emptyFile_zeroBytes_producesError_notCrashNotParsed() {
        val outcome = ExpenseCsvParser.parse("")

        assertTrue(outcome is CsvImportOutcome.Error)
    }

    @Test
    fun allWhitespaceFile_producesError() {
        val outcome = ExpenseCsvParser.parse("   \n\n   \t\n")

        assertTrue(outcome is CsvImportOutcome.Error)
    }

    @Test
    fun headerOnlyFile_producesParsedWithEmptyLists_notError() {
        // Per current implementation this is treated as a valid (trivial) import, not an error.
        // Judgment call: arguably a header-only file could be considered "nothing to import" and
        // flagged, but treating it as a valid empty parse is a reasonable, unsurprising behavior
        // for a CSV importer, so this is not reported as a bug.
        val outcome = ExpenseCsvParser.parse(header)

        assertTrue(outcome is CsvImportOutcome.Parsed)
        val result = (outcome as CsvImportOutcome.Parsed).result
        assertTrue(result.validExpenses.isEmpty())
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun malformedHeader_wrongColumnNames_producesError() {
        val csv = "foo,bar,baz,qux,quux\nA,100,Shopping,2024-01-01,\n"

        val outcome = ExpenseCsvParser.parse(csv)

        assertTrue(outcome is CsvImportOutcome.Error)
    }

    @Test
    fun malformedHeader_completelyDifferentContent_producesError() {
        val csv = "This is not a CSV file at all.\nJust some prose."

        val outcome = ExpenseCsvParser.parse(csv)

        assertTrue(outcome is CsvImportOutcome.Error)
    }

    // ---- mixed valid + multiple failure types in one parse ----

    @Test
    fun mixedValidAndMultipleFailureTypes_countsAndReasonsAllCorrect() {
        val csv =
            """
            $header
            Coffee,500,Shopping,2024-01-15,
            BadCategory,100,NotACategory,2024-01-01,
            BadAmount,notanumber,Shopping,2024-01-01,
            BadDate,100,Shopping,not-a-date,
            TooFewColumns,100,Shopping,2024-01-01
            Rent,150000,Recurring,2024-02-01,monthly rent
            """.trimIndent()

        val result = parsed(csv)

        assertEquals(2, result.validExpenses.size)
        assertEquals("Coffee", result.validExpenses[0].name)
        assertEquals("Rent", result.validExpenses[1].name)

        assertEquals(4, result.failures.size)
        val byRow = result.failures.associateBy { it.rowNumber }
        assertTrue(byRow.getValue(3).reason.contains("category", ignoreCase = true))
        assertTrue(byRow.getValue(4).reason.contains("amountCents", ignoreCase = true))
        assertTrue(byRow.getValue(5).reason.contains("date", ignoreCase = true))
        assertTrue(byRow.getValue(6).reason.contains("5"))
    }
}
