// The category palette below maps each color key to a pair of brand-hex Color literals; the key
// already documents what each literal means, so a `private const val` per literal would just
// rename it a second time. Suppress at the file level instead.
@file:Suppress("MagicNumber")

package com.joaoeoneves.fintrack.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.ui.theme.LocalDarkTheme

/**
 * Human-readable label for a [Category], shared across dashboard, list, and add/edit screens: the
 * user's own name, else the built-in's localized name, else "Unknown category". `@Composable`
 * because it resolves through [stringResource]; every call site is inside a Composable already.
 */
val Category.displayName: String
    @Composable
    get() = customName ?: stringResource(builtInNameRes[id] ?: R.string.category_unknown)

/** A category color in light and dark theme. */
private data class CategoryColor(
    val light: Color,
    val dark: Color,
)

const val FALLBACK_COLOR_KEY = "slate"

/**
 * Category palette by color key: light-mode brand hues (Tailwind 600) and their dark-mode variants
 * (Tailwind 500), tuned for legible contrast on each surface. The first five are the built-ins'.
 */
private val categoryPalette: Map<String, CategoryColor> =
    linkedMapOf(
        "blue" to CategoryColor(Color(0xFF2563EB), Color(0xFF3B82F6)),
        "emerald" to CategoryColor(Color(0xFF059669), Color(0xFF10B981)),
        "amber" to CategoryColor(Color(0xFFD97706), Color(0xFFF59E0B)),
        "violet" to CategoryColor(Color(0xFF7C3AED), Color(0xFF8B5CF6)),
        "red" to CategoryColor(Color(0xFFDC2626), Color(0xFFEF4444)),
        "pink" to CategoryColor(Color(0xFFDB2777), Color(0xFFEC4899)),
        "teal" to CategoryColor(Color(0xFF0D9488), Color(0xFF14B8A6)),
        "orange" to CategoryColor(Color(0xFFEA580C), Color(0xFFF97316)),
        "indigo" to CategoryColor(Color(0xFF4F46E5), Color(0xFF6366F1)),
        "lime" to CategoryColor(Color(0xFF65A30D), Color(0xFF84CC16)),
        "cyan" to CategoryColor(Color(0xFF0891B2), Color(0xFF06B6D4)),
        FALLBACK_COLOR_KEY to CategoryColor(Color(0xFF475569), Color(0xFF64748B)),
    )

/** Every selectable category color key, in palette order. */
val categoryColorKeys: List<String> = categoryPalette.keys.toList()

/**
 * Plain (non-composable), unit-testable resolver for a category's color given whether the dark
 * theme is active. Unknown color keys fall back to slate.
 */
fun categoryColorFor(
    colorKey: String,
    isDarkTheme: Boolean,
): Color {
    val color = categoryPalette[colorKey] ?: categoryPalette.getValue(FALLBACK_COLOR_KEY)
    return if (isDarkTheme) color.dark else color.light
}

/** The palette color of the [index]th slice of a chart, cycling through the palette (slate last is skipped). */
fun sliceColorFor(
    index: Int,
    isDarkTheme: Boolean,
): Color = categoryColorFor(categoryColorKeys[index.mod(categoryColorKeys.size - 1)], isDarkTheme)

/** [categoryColorFor] a [category]'s own color key. */
fun categoryColorFor(
    category: Category,
    isDarkTheme: Boolean,
): Color = categoryColorFor(category.colorKey, isDarkTheme)

/**
 * Theme-aware color per [Category], used by the pie chart and its legend.
 */
val Category.color: Color
    @Composable
    get() = categoryColorFor(this, LocalDarkTheme.current)

/** Category icons by icon key. The first five are the built-ins'. */
private val categoryIcons: Map<String, ImageVector> =
    linkedMapOf(
        "swap_horiz" to Icons.Filled.SwapHoriz,
        "trending_up" to Icons.AutoMirrored.Filled.TrendingUp,
        "shopping_cart" to Icons.Filled.ShoppingCart,
        "restaurant" to Icons.Filled.Restaurant,
        "autorenew" to Icons.Filled.Autorenew,
        "home" to Icons.Filled.Home,
        "directions_car" to Icons.Filled.DirectionsCar,
        "local_gas_station" to Icons.Filled.LocalGasStation,
        "flight" to Icons.Filled.Flight,
        "medical_services" to Icons.Filled.MedicalServices,
        "school" to Icons.Filled.School,
        "pets" to Icons.Filled.Pets,
        "fitness_center" to Icons.Filled.FitnessCenter,
        "sports_esports" to Icons.Filled.SportsEsports,
        "local_cafe" to Icons.Filled.LocalCafe,
        "checkroom" to Icons.Filled.Checkroom,
        "card_giftcard" to Icons.Filled.CardGiftcard,
        "savings" to Icons.Filled.Savings,
        "bolt" to Icons.Filled.Bolt,
        "movie" to Icons.Filled.Movie,
        "label" to Icons.AutoMirrored.Filled.Label,
    )

/** Every selectable category icon key, in display order. */
val categoryIconKeys: List<String> = categoryIcons.keys.toList()

/**
 * Icon per [Category], used alongside the colored dot in the pie chart legend and expense rows.
 * Unknown icon keys (including the unknown-category placeholder's) show a question mark.
 */
val Category.icon: ImageVector
    get() = categoryIconFor(iconKey)

/** Icon for an icon key; unknown keys show a question mark. */
fun categoryIconFor(iconKey: String): ImageVector = categoryIcons[iconKey] ?: Icons.AutoMirrored.Filled.HelpOutline
