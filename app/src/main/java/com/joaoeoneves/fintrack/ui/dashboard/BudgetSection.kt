package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.ui.common.color
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.icon

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

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            budgets.forEach { budget ->
                BudgetRow(
                    budget = budget,
                    onClick = { editingBudget = budget },
                )
            }
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
    val progress =
        if (budget.limitCents != null && budget.limitCents > 0) {
            (budget.spentCents.toFloat() / budget.limitCents.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val progressColor = if (budget.isOverBudget) MaterialTheme.colorScheme.error else budget.category.color

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BudgetCategoryLabel(category = budget.category)
            BudgetStatusText(budget = budget, modifier = Modifier.weight(1f))
        }
        LinearProgressIndicator(
            progress = { progress },
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun BudgetCategoryLabel(category: Category) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(category.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = category.color,
            )
        }
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

// Weighted so long, wrapped translations of this status text are confined to their own
// right-hand column and never overlap the category name on the left (see bug report).
@Composable
private fun BudgetStatusText(
    budget: CategoryBudget,
    modifier: Modifier = Modifier,
) {
    val textColor = if (budget.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Text(
        text =
            if (budget.limitCents != null) {
                stringResource(
                    R.string.budget_spent_of_limit,
                    formatAmountCents(budget.spentCents),
                    formatAmountCents(budget.limitCents),
                )
            } else {
                stringResource(R.string.budget_spent_no_limit, formatAmountCents(budget.spentCents))
            },
        style = MaterialTheme.typography.bodySmall,
        color = textColor,
        textAlign = TextAlign.End,
        modifier = modifier,
    )
}
