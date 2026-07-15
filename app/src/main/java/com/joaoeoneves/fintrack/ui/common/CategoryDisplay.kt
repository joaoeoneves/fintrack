package com.joaoeoneves.fintrack.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.ui.theme.LocalDarkTheme

/**
 * Human-readable label for an [ExpenseCategory], shared across dashboard, list, and add/edit screens.
 */
val ExpenseCategory.displayName: String
    get() =
        when (this) {
            ExpenseCategory.TRANSFER -> "Transfer"
            ExpenseCategory.INVESTMENTS -> "Investments"
            ExpenseCategory.SHOPPING -> "Shopping"
            ExpenseCategory.RECURRING -> "Recurring"
        }

/**
 * Light-mode color per [ExpenseCategory] (Google Material brand hues).
 */
private fun lightCategoryColor(category: ExpenseCategory): Color =
    when (category) {
        ExpenseCategory.TRANSFER -> Color(0xFF2563EB)
        ExpenseCategory.INVESTMENTS -> Color(0xFF059669)
        ExpenseCategory.SHOPPING -> Color(0xFFD97706)
        ExpenseCategory.RECURRING -> Color(0xFFDC2626)
    }

/**
 * Dark-mode color per [ExpenseCategory] — the same brand identity, tuned to Google's published
 * dark-mode tone variants for legible contrast on a dark surface.
 */
private fun darkCategoryColor(category: ExpenseCategory): Color =
    when (category) {
        ExpenseCategory.TRANSFER -> Color(0xFF3B82F6)
        ExpenseCategory.INVESTMENTS -> Color(0xFF10B981)
        ExpenseCategory.SHOPPING -> Color(0xFFF59E0B)
        ExpenseCategory.RECURRING -> Color(0xFFEF4444)
    }

/**
 * Plain (non-composable), unit-testable resolver for a category's color given whether the dark
 * theme is active.
 */
fun categoryColorFor(
    category: ExpenseCategory,
    isDarkTheme: Boolean,
): Color = if (isDarkTheme) darkCategoryColor(category) else lightCategoryColor(category)

/**
 * Theme-aware color per [ExpenseCategory], used by the pie chart and its legend.
 */
val ExpenseCategory.color: Color
    @Composable
    get() = categoryColorFor(this, LocalDarkTheme.current)

/**
 * Icon per [ExpenseCategory], used alongside the colored dot in the pie chart legend and expense rows.
 */
val ExpenseCategory.icon: ImageVector
    get() =
        when (this) {
            ExpenseCategory.TRANSFER -> Icons.Filled.SwapHoriz
            ExpenseCategory.INVESTMENTS -> Icons.Filled.TrendingUp
            ExpenseCategory.SHOPPING -> Icons.Filled.ShoppingCart
            ExpenseCategory.RECURRING -> Icons.Filled.Autorenew
        }
