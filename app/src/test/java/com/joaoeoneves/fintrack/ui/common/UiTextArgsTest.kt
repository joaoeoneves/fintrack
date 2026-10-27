package com.joaoeoneves.fintrack.ui.common

import android.app.Application
import com.joaoeoneves.fintrack.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** [UiText.StringResource] format args are applied by [asString] (Resources overload). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UiTextArgsTest {
    private fun resources() = RuntimeEnvironment.getApplication().resources

    @Test
    fun noArgs_defaultsToEmptyList() {
        assertEquals(emptyList<Any>(), UiText.StringResource(R.string.error_csv_empty).args)
    }

    @Test
    fun intArg_isFormatted_english() {
        val text = UiText.StringResource(R.string.error_csv_row_columns, listOf(4))
        assertEquals("Expected 5 columns, found 4", text.asString(resources()))
    }

    @Test
    fun stringArg_isFormatted_englishAndEchoesRawValue() {
        val text = UiText.StringResource(R.string.error_csv_row_date_invalid, listOf(" not-a-date "))
        assertEquals("Invalid date: ' not-a-date '", text.asString(resources()))
    }

    @Test
    @Config(qualifiers = "pt-rPT")
    fun intArg_isFormatted_portuguese() {
        val text = UiText.StringResource(R.string.error_csv_row_name_too_long, listOf(200))
        assertEquals("O nome é demasiado longo (máximo: 200)", text.asString(resources()))
    }

    @Test
    @Config(qualifiers = "pt-rPT")
    fun stringArg_isFormatted_portuguese() {
        val text = UiText.StringResource(R.string.error_csv_row_category_unknown, listOf("Gym"))
        assertEquals("Categoria desconhecida: 'Gym'", text.asString(resources()))
    }
}
