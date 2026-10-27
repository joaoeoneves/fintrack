package com.joaoeoneves.fintrack.ui.importexport

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.csv.CsvImportError
import com.joaoeoneves.fintrack.data.csv.CsvRowError
import com.joaoeoneves.fintrack.ui.common.UiText

/** Maps a fatal CSV import error to localized user-facing text. */
fun CsvImportError.toUiText(): UiText =
    when (this) {
        CsvImportError.Empty -> UiText.StringResource(R.string.error_csv_empty)
        CsvImportError.UnrecognizedHeader -> UiText.StringResource(R.string.error_csv_header)
    }

/** Maps a per-row CSV failure reason to localized user-facing text. */
fun CsvRowError.toUiText(): UiText =
    when (this) {
        is CsvRowError.WrongColumnCount -> UiText.StringResource(R.string.error_csv_row_columns, listOf(found))
        CsvRowError.NameRequired -> UiText.StringResource(R.string.error_csv_row_name_required)
        is CsvRowError.NameTooLong -> UiText.StringResource(R.string.error_csv_row_name_too_long, listOf(max))
        is CsvRowError.InvalidAmount -> UiText.StringResource(R.string.error_csv_row_amount_invalid, listOf(raw))
        is CsvRowError.ZeroAmount -> UiText.StringResource(R.string.error_csv_row_amount_zero, listOf(raw))
        is CsvRowError.UnknownCategory -> UiText.StringResource(R.string.error_csv_row_category_unknown, listOf(raw))
        is CsvRowError.InvalidDate -> UiText.StringResource(R.string.error_csv_row_date_invalid, listOf(raw))
        is CsvRowError.NoteTooLong -> UiText.StringResource(R.string.error_csv_row_note_too_long, listOf(max))
    }
