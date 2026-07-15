package ptech.joaoe.agenticusage.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import ptech.joaoe.agenticusage.ui.theme.LocalDarkTheme

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
        ExpenseCategory.TRANSFER -> Color(0xFF4285F4)
        ExpenseCategory.INVESTMENTS -> Color(0xFF34A853)
        ExpenseCategory.SHOPPING -> Color(0xFFFBBC05)
        ExpenseCategory.RECURRING -> Color(0xFFEA4335)
    }

/**
 * Dark-mode color per [ExpenseCategory] — the same brand identity, tuned to Google's published
 * dark-mode tone variants for legible contrast on a dark surface.
 */
private fun darkCategoryColor(category: ExpenseCategory): Color =
    when (category) {
        ExpenseCategory.TRANSFER -> Color(0xFF8AB4F8)
        ExpenseCategory.INVESTMENTS -> Color(0xFF81C995)
        ExpenseCategory.SHOPPING -> Color(0xFFFDD663)
        ExpenseCategory.RECURRING -> Color(0xFFF28B82)
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
