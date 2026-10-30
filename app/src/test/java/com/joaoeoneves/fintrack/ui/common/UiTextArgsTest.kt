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
        assertEquals(emptyList<Any>(), UiText.StringResource(R.string.activity_evolution).args)
    }

    @Test
    fun intArg_isFormatted_english() {
        val text = UiText.StringResource(R.string.cd_category_icon_option, listOf(4))
        assertEquals("Icon 4", text.asString(resources()))
    }

    @Test
    fun stringArg_isFormatted_englishAndEchoesRawValue() {
        val text = UiText.StringResource(R.string.category_move_nothing, listOf(" Gym "))
        assertEquals("Nothing uses “ Gym ”, so there is nothing to move.", text.asString(resources()))
    }

    @Test
    @Config(qualifiers = "pt-rPT")
    fun intArg_isFormatted_portuguese() {
        val text = UiText.StringResource(R.string.cd_category_icon_option, listOf(12))
        assertEquals("Ícone 12", text.asString(resources()))
    }

    @Test
    @Config(qualifiers = "pt-rPT")
    fun stringArg_isFormatted_portuguese() {
        val text = UiText.StringResource(R.string.category_move_nothing, listOf("Gym"))
        assertEquals("Nada usa “Gym”, por isso não há nada para mover.", text.asString(resources()))
    }
}
