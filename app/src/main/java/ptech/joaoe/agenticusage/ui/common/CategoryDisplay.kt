package ptech.joaoe.agenticusage.ui.common

import androidx.compose.ui.graphics.Color
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
