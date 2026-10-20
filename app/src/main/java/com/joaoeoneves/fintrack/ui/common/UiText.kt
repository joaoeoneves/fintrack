package com.joaoeoneves.fintrack.ui.common

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * User-facing text produced by a ViewModel without it needing a [android.content.Context]: either
 * a raw [Dynamic] string (e.g. an exception message) or a [StringResource] the UI resolves in the
 * current locale.
 */
sealed interface UiText {
    data class Dynamic(
        val value: String,
    ) : UiText

    data class StringResource(
        @param:StringRes val resId: Int,
    ) : UiText
}

/** Resolves this text inside a Composable, in the current locale. */
@Composable
fun UiText.asString(): String =
    when (this) {
        is UiText.Dynamic -> value
        is UiText.StringResource -> stringResource(resId)
    }

/** Resolves this text outside composition (e.g. inside a `LaunchedEffect`). */
fun UiText.asString(resources: Resources): String =
    when (this) {
        is UiText.Dynamic -> value
        is UiText.StringResource -> resources.getString(resId)
    }

/** This throwable's message as [UiText.Dynamic], or [fallback] when it has none. */
fun Throwable?.messageOr(
    @StringRes fallback: Int,
): UiText = this?.message?.let(UiText::Dynamic) ?: UiText.StringResource(fallback)
