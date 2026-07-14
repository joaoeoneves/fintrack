package ptech.joaoe.agenticusage.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory

/**
 * Human-readable label for an [ExpenseCategory], shared across dashboard, list, and add/edit screens.
 */
val ExpenseCategory.displayName: String
    get() = when (this) {
        ExpenseCategory.TRANSFER -> "Transfer"
        ExpenseCategory.INVESTMENTS -> "Investments"
        ExpenseCategory.SHOPPING -> "Shopping"
        ExpenseCategory.RECURRING -> "Recurring"
    }

/**
 * Fixed color per [ExpenseCategory], used by the pie chart and its legend.
 */
val ExpenseCategory.color: Color
    get() = when (this) {
        ExpenseCategory.TRANSFER -> Color(0xFF4285F4)
        ExpenseCategory.INVESTMENTS -> Color(0xFF34A853)
        ExpenseCategory.SHOPPING -> Color(0xFFFBBC05)
        ExpenseCategory.RECURRING -> Color(0xFFEA4335)
    }

/**
 * Icon per [ExpenseCategory], used alongside the colored dot in the pie chart legend and expense rows.
 */
val ExpenseCategory.icon: ImageVector
    get() = when (this) {
        ExpenseCategory.TRANSFER -> Icons.Filled.SwapHoriz
        ExpenseCategory.INVESTMENTS -> Icons.Filled.TrendingUp
        ExpenseCategory.SHOPPING -> Icons.Filled.ShoppingCart
        ExpenseCategory.RECURRING -> Icons.Filled.Autorenew
    }
