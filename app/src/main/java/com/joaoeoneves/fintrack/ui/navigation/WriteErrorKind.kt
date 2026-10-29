package com.joaoeoneves.fintrack.ui.navigation

import androidx.annotation.StringRes
import com.joaoeoneves.fintrack.R

/** The two things a rejected write can be told to the user as; the raw server text is only ever logged. */
enum class WriteErrorKind { PERMISSION, OTHER }

/**
 * The kind of a rejected write from its [reason].
 *
 * TODO(5.3-merge): switch to Track A's WriteErrors error code (`PERMISSION_DENIED` -> [WriteErrorKind.PERMISSION]);
 * until then the code is looked for in the reason text.
 */
fun writeErrorKindOf(reason: String): WriteErrorKind =
    if (reason.contains("PERMISSION_DENIED", ignoreCase = true)) WriteErrorKind.PERMISSION else WriteErrorKind.OTHER

/** The fixed, localised message for [kind]. */
@StringRes
fun writeErrorMessageRes(kind: WriteErrorKind): Int =
    when (kind) {
        WriteErrorKind.PERMISSION -> R.string.error_write_permission
        WriteErrorKind.OTHER -> R.string.error_write_rejected
    }
