package com.joaoeoneves.fintrack.data.csv

import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

private const val EXPECTED_HEADER = "name,amountcents,category,date,note"
private const val EXPECTED_COLUMN_COUNT = 5

/** A single CSV data row that failed to parse into an [Expense]. */
data class CsvRowFailure(
    val rowNumber: Int,
    val rawLine: String,
    val reason: String,
)

/** Result of parsing a whole CSV document: successfully parsed expenses plus any row failures. */
data class CsvParseResult(
    val validExpenses: List<Expense>,
    val failures: List<CsvRowFailure>,
)

/** Outcome of attempting to parse a CSV document: either a (possibly partial) [CsvParseResult], or a
 * fatal error that prevented parsing from proceeding at all (empty file, bad header). */
sealed interface CsvImportOutcome {
    data class Parsed(
        val result: CsvParseResult,
    ) : CsvImportOutcome

    data class Error(
        val message: String,
    ) : CsvImportOutcome
}

/**
 * Parses expense CSV text in the format documented by the `seed-data` skill:
 * header `name,amountCents,category,date,note`, one row per expense.
 */
object ExpenseCsvParser {
    fun parse(csvText: String): CsvImportOutcome {
        val normalizedText = csvText.removePrefix("﻿")
        val lines =
            splitCsvRecords(normalizedText)
                .let { if (it.isNotEmpty() && it.last().isBlank()) it.dropLast(1) else it }
        val nonBlankLines = lines.filter { it.isNotBlank() }

        if (nonBlankLines.isEmpty()) {
            return CsvImportOutcome.Error("The file is empty")
        }

        val header = nonBlankLines.first()
        val headerColumns = splitCsvLine(header).map { it.trim().lowercase() }
        if (headerColumns.joinToString(",") != EXPECTED_HEADER) {
            return CsvImportOutcome.Error(
                "Unrecognized header — expected name,amountCents,category,date,note",
            )
        }

        val dataLines = nonBlankLines.drop(1)
        if (dataLines.isEmpty()) {
            return CsvImportOutcome.Parsed(CsvParseResult(emptyList(), emptyList()))
        }

        val validExpenses = mutableListOf<Expense>()
        val failures = mutableListOf<CsvRowFailure>()

        dataLines.forEachIndexed { index, line ->
            val rowNumber = index + 2 // header is row 1, first data row is row 2
            val fields = splitCsvLine(line)
            if (fields.size != EXPECTED_COLUMN_COUNT) {
                failures += CsvRowFailure(rowNumber, line, "Expected 5 columns, found ${fields.size}")
                return@forEachIndexed
            }

            val (rawName, rawAmount, rawCategory, rawDate, rawNote) = fields

            val name = rawName.trim()
            if (name.isEmpty()) {
                failures += CsvRowFailure(rowNumber, line, "Name is required")
                return@forEachIndexed
            }

            val amountCents = rawAmount.trim().toLongOrNull()
            if (amountCents == null) {
                failures += CsvRowFailure(rowNumber, line, "Invalid amountCents value: '$rawAmount'")
                return@forEachIndexed
            }

            val category = parseCategory(rawCategory.trim())
            if (category == null) {
                failures += CsvRowFailure(rowNumber, line, "Unknown category: '$rawCategory'")
                return@forEachIndexed
            }

            val date = parseDate(rawDate.trim())
            if (date == null) {
                failures += CsvRowFailure(rowNumber, line, "Invalid date: '$rawDate'")
                return@forEachIndexed
            }

            val note = rawNote.ifBlank { null }

            validExpenses +=
                Expense(
                    id = "",
                    name = name,
                    amountCents = amountCents,
                    category = category,
                    date = date,
                    note = note,
                )
        }

        return CsvImportOutcome.Parsed(CsvParseResult(validExpenses, failures))
    }

    private fun parseCategory(raw: String): ExpenseCategory? =
        ExpenseCategory.entries.find { category ->
            category.name.equals(raw, ignoreCase = true) || category.canonicalName().equals(raw, ignoreCase = true)
        }

    private fun ExpenseCategory.canonicalName(): String =
        when (this) {
            ExpenseCategory.TRANSFER -> "Transfer"
            ExpenseCategory.INVESTMENTS -> "Investments"
            ExpenseCategory.SHOPPING -> "Shopping"
            ExpenseCategory.RECURRING -> "Recurring"
        }

    private fun parseDate(raw: String): Instant? =
        try {
            Instant.parse(raw)
        } catch (e: DateTimeParseException) {
            try {
                LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant()
            } catch (e2: DateTimeParseException) {
                null
            }
        }

    /**
     * Splits the raw CSV document text into logical records, scanning character-by-character and
     * tracking whether we're inside an open quoted field (toggled on `"`, with `""` treated as an
     * escaped quote that stays inside the field). Only treats `\n`/`\r`/`\r\n` as a record
     * separator when NOT inside an open quote, so a quoted multi-line note (a valid CSV field
     * containing an embedded newline) is kept as a single logical record.
     */
    private fun splitCsvRecords(text: String): List<String> {
        val records = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '"') {
                current.append(c)
                if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
                    current.append(text[i + 1])
                    i += 2
                    continue
                }
                inQuotes = !inQuotes
                i++
                continue
            }
            if (!inQuotes && (c == '\n' || c == '\r')) {
                records += current.toString()
                current.setLength(0)
                i += if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') 2 else 1
                continue
            }
            current.append(c)
            i++
        }
        records += current.toString()
        return records
    }

    /**
     * Splits a single CSV line into its raw fields, supporting double-quoted fields (with doubled
     * `""` as an escaped quote) so a comma or quote inside a field can round-trip.
     */
    private fun splitCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            current.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        current.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    fields += current.toString()
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }
}
