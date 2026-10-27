// This file's sole purpose is naming hex color literals as top-level palette constants; the
// `val SomeColorName = Color(0xFF...)` declarations are already self-documenting, so extracting a
// `private const val ..._HEX` above each one would just rename the same literal twice.
@file:Suppress("MagicNumber")

package com.joaoeoneves.fintrack.ui.theme

import androidx.compose.ui.graphics.Color

// Monochrome palette -- dark scheme
val DarkBg = Color(0xFF000000)
val DarkSurface1 = Color(0xFF0E0E10)
val DarkSurface2 = Color(0xFF1A1A1D)
val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0x8CFFFFFF) // white at 55%
val DarkHairline = Color(0x14FFFFFF) // white at 8%
val DarkPositive = Color(0xFF00C46A)
val DarkNegative = Color(0xFFFF4D4D)
val DarkAccent = Color(0xFFFFFFFF)
val DarkOnAccent = Color(0xFF000000)

// Monochrome palette -- light scheme
val LightBg = Color(0xFFFFFFFF)
val LightSurface1 = Color(0xFFF4F4F5)
val LightSurface2 = Color(0xFFEBEBED)
val LightTextPrimary = Color(0xFF0A0A0A)
val LightTextSecondary = Color(0x8C0A0A0A) // text at 55%
val LightHairline = Color(0x14000000) // black at 8%
val LightPositive = Color(0xFF00A35A)
val LightNegative = Color(0xFFE5383B)
val LightAccent = Color(0xFF000000)
val LightOnAccent = Color(0xFFFFFFFF)
