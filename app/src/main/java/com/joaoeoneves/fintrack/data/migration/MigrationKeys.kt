package com.joaoeoneves.fintrack.data.migration

import java.text.Normalizer

internal fun String.nameKey(): String = trim().lowercase()

internal val COMBINING_MARKS = Regex("\\p{M}+")
internal val WHITESPACE = Regex("\\s+")

/** A label compared ignoring case, accents and extra spaces. */
internal fun String.labelKey(): String =
    Normalizer
        .normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace(WHITESPACE, " ")

internal fun nameKindKey(
    name: String,
    kind: String,
): String = "$kind:${name.nameKey()}"
