package com.joaoeoneves.fintrack.ui.common

import android.app.Application
import com.joaoeoneves.fintrack.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** [UiText.asString] (Resources overload) and [messageOr], against real Robolectric resources. */
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

    @Test
    fun messageOr_throwableWithMessage_returnsDynamicMessage() {
        assertEquals(
            UiText.Dynamic("boom"),
            IllegalStateException("boom").messageOr(R.string.error_generic_fallback),
        )
    }

    @Test
    fun messageOr_throwableWithNullMessage_returnsFallbackResource() {
        assertEquals(
            UiText.StringResource(R.string.error_generic_fallback),
            RuntimeException().messageOr(R.string.error_generic_fallback),
        )
    }

    @Test
    fun messageOr_nullThrowable_returnsFallbackResource() {
        val none: Throwable? = null

        assertEquals(UiText.StringResource(R.string.error_import_failed), none.messageOr(R.string.error_import_failed))
    }

    @Test
    fun messageOr_emptyMessage_isKeptAsDynamic_notReplacedByFallback() {
        // Only a *null* message falls back; an empty one is passed through as-is.
        assertEquals(UiText.Dynamic(""), IllegalStateException("").messageOr(R.string.error_generic_fallback))
    }
}
