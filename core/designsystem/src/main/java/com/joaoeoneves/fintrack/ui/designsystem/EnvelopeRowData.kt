package com.joaoeoneves.fintrack.ui.designsystem

/**
 * What an [EnvelopeRow] shows: the [name] (wraps to two lines, then ellipsizes), an optional right-aligned
 * [amountText] ("spent of limit"; [amountSemantics] is what screen readers get when the shown text is counting),
 * a thin bar when [progress] is given, the plain [caption] and, when overspent, the [overText] (negative colour,
 * so overspend never relies on colour alone). Rows without a limit pass no [progress]/[amountText]; they never
 * show a zero limit.
 */
data class EnvelopeRowData(
    val name: String,
    val amountText: String? = null,
    val amountSemantics: String? = null,
    val isOver: Boolean = false,
    val progress: Float? = null,
    val caption: String? = null,
    val overText: String? = null,
)
