package com.joaoeoneves.fintrack.ui.importexport

import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.csv.CsvImportError
import com.joaoeoneves.fintrack.data.csv.CsvRowError
import com.joaoeoneves.fintrack.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mapping of typed CSV errors to [UiText] resource ids and format args. */
class CsvErrorTextTest {
    @Test
    fun importError_empty_mapsToEmptyResourceWithoutArgs() {
        assertEquals(UiText.StringResource(R.string.error_csv_empty), CsvImportError.Empty.toUiText())
    }

    @Test
    fun importError_unrecognizedHeader_mapsToHeaderResource() {
        assertEquals(UiText.StringResource(R.string.error_csv_header), CsvImportError.UnrecognizedHeader.toUiText())
    }

    @Test
    fun rowError_wrongColumnCount_passesFoundCount() {
        assertEquals(
            UiText.StringResource(R.string.error_csv_row_columns, listOf(4)),
            CsvRowError.WrongColumnCount(4).toUiText(),
        )
    }

    @Test
    fun rowError_nameRequired_hasNoArgs() {
        assertEquals(UiText.StringResource(R.string.error_csv_row_name_required), CsvRowError.NameRequired.toUiText())
    }

    @Test
    fun rowError_nameTooLong_passesMax() {
        assertEquals(
            UiText.StringResource(R.string.error_csv_row_name_too_long, listOf(200)),
            CsvRowError.NameTooLong(200).toUiText(),
        )
    }

    @Test
    fun rowError_noteTooLong_passesMax() {
        assertEquals(
            UiText.StringResource(R.string.error_csv_row_note_too_long, listOf(2000)),
            CsvRowError.NoteTooLong(2000).toUiText(),
        )
    }

    @Test
    fun rowError_invalidAndZeroAmount_passRawValue() {
        assertEquals(
            UiText.StringResource(R.string.error_csv_row_amount_invalid, listOf(" abc ")),
            CsvRowError.InvalidAmount(" abc ").toUiText(),
        )
        assertEquals(
            UiText.StringResource(R.string.error_csv_row_amount_zero, listOf("-0")),
            CsvRowError.ZeroAmount("-0").toUiText(),
        )
    }

    @Test
    fun rowError_unknownCategoryAndInvalidDate_passRawValue() {
        assertEquals(
            UiText.StringResource(R.string.error_csv_row_category_unknown, listOf("Gym")),
            CsvRowError.UnknownCategory("Gym").toUiText(),
        )
        assertEquals(
            UiText.StringResource(R.string.error_csv_row_date_invalid, listOf("not-a-date")),
            CsvRowError.InvalidDate("not-a-date").toUiText(),
        )
    }
}
