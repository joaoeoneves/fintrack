package com.joaoeoneves.fintrack.ui.navigation

import androidx.annotation.StringRes
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.WriteErrorCode

/** The two things a rejected write can be told to the user as; the raw server text is only ever logged. */
enum class WriteErrorKind { PERMISSION, OTHER }

/** The kind of a rejected write from its [code]. */
fun writeErrorKindOf(code: WriteErrorCode): WriteErrorKind =
    when (code) {
        WriteErrorCode.PERMISSION_DENIED -> WriteErrorKind.PERMISSION
        WriteErrorCode.OTHER -> WriteErrorKind.OTHER
    }

/** The fixed, localised message for [kind]. */
@StringRes
fun writeErrorMessageRes(kind: WriteErrorKind): Int =
    when (kind) {
        WriteErrorKind.PERMISSION -> R.string.error_write_permission
        WriteErrorKind.OTHER -> R.string.error_write_rejected
    }
