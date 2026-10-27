package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.icon
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import kotlin.math.roundToInt

/** How much of the limit is used, as a 0..1 bar fill: 0 without a (positive) limit, capped at 1 when over. */
fun CategoryBudget.progressFraction(): Float {
    val limit = limitCents?.takeIf { it > 0L } ?: return 0f
    return (spentCents.toDouble() / limit.toDouble()).toFloat().coerceIn(0f, 1f)
}

/** Whole-number percent of the limit spent, not capped (130 when 30% over); 0 without a (positive) limit. */
fun CategoryBudget.percentUsed(): Int {
    val limit = limitCents?.takeIf { it > 0L } ?: return 0
    return (spentCents.toDouble() / limit.toDouble() * PERCENT_SCALE).roundToInt()
}

private const val PERCENT_SCALE = 100

/**
 * Shows per-category budget progress for the dashboard's budget month (described by [title]), with
 * the ability to tap a row to set, change or remove that category's limit.
 */
@Composable
fun BudgetSection(
    budgets: List<CategoryBudget>,
    title: String,
    onSetBudget: (Category, Long) -> Unit,
    onClearBudget: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingBudget by remember { mutableStateOf<CategoryBudget?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title = title)
        budgets.forEach { budget ->
            BudgetRow(budget = budget, onClick = { editingBudget = budget })
        }
    }

    editingBudget?.let { budget ->
        EditBudgetDialog(
            budget = budget,
            onSave = { limitCents ->
                onSetBudget(budget.category, limitCents)
                editingBudget = null
            },
            onRemove = {
                onClearBudget(budget.category)
                editingBudget = null
            },
            onDismiss = { editingBudget = null },
        )
    }
}

@Composable
private fun BudgetRow(
    budget: CategoryBudget,
    onClick: () -> Unit,
) {
    val secondary = FinTrackTheme.colors.textSecondary
    val statusColor = if (budget.isOverBudget) FinTrackTheme.colors.negative else secondary
    val locale = currentLocale()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = Dimens.Space12),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
        verticalAlignment = Alignment.Top,
    ) {
        BudgetIcon(budget.category)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = budget.category.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (budget.limitCents != null) {
                    Text(
                        text = formatSavingsRate(budget.percentUsed() / PERCENT_SCALE.toDouble(), locale),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = statusColor,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = Dimens.Space8),
                    )
                }
            }
            Text(
                text = budgetStatus(budget),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = statusColor,
            )
            if (budget.limitCents != null) {
                ThinProgressBar(
                    progress = budget.progressFraction(),
                    isOver = budget.isOverBudget,
                    modifier = Modifier.padding(top = Dimens.Space8),
                )
            }
        }
    }
}

@Composable
private fun BudgetIcon(category: Category) {
    Box(
        modifier =
            Modifier
                .size(Dimens.IconBubble)
                .clip(CircleShape)
                .background(FinTrackTheme.colors.surface2),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun budgetStatus(budget: CategoryBudget): String =
    if (budget.limitCents != null) {
        stringResource(
            R.string.budget_spent_of_limit,
            formatAmountCents(budget.spentCents),
            formatAmountCents(budget.limitCents),
        )
    } else {
        stringResource(R.string.budget_spent_no_limit, formatAmountCents(budget.spentCents))
    }
