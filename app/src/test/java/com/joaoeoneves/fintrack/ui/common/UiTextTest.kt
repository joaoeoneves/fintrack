package com.joaoeoneves.fintrack.ui.common

import android.app.Application
import com.joaoeoneves.fintrack.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** [UiText.asString] (Resources overload), against real Robolectric resources. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UiTextTest {
    private fun resources() = RuntimeEnvironment.getApplication().resources

    @Test
    fun dynamic_asString_returnsRawValue() {
        assertEquals("Firestore unavailable", UiText.Dynamic("Firestore unavailable").asString(resources()))
    }

    @Test
    fun dynamic_emptyValue_asString_returnsEmpty() {
        assertEquals("", UiText.Dynamic("").asString(resources()))
    }

    @Test
    fun stringResource_asString_resolvesEnglish() {
        assertEquals("Sign-out failed", UiText.StringResource(R.string.error_sign_out_failed).asString(resources()))
    }

    @Test
    @Config(qualifiers = "pt-rPT")
    fun stringResource_asString_resolvesPortuguese() {
        assertEquals(
            "Falha ao terminar sessão",
            UiText.StringResource(R.string.error_sign_out_failed).asString(resources()),
        )
    }
}
