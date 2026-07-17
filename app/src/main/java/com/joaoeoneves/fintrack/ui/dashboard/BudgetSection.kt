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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.ui.common.color
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.icon
import com.joaoeoneves.fintrack.ui.expense.addedit.parseAmountCents

/**
 * Shows per-category budget progress for the current calendar month, with the ability to tap a
 * row to set or change that category's limit.
 */
@Composable
fun BudgetSection(
    budgets: List<CategoryBudget>,
    onSetBudget: (ExpenseCategory, Long) -> Unit,
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
                text = stringResource(R.string.dashboard_budgets_title),
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(budget.category.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = budget.category.icon,
                        contentDescription = null,
                        tint = budget.category.color,
                    )
                }
                Text(
                    text = budget.category.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            val textColor =
                if (budget.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
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
private fun EditBudgetDialog(
    budget: CategoryBudget,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf(budget.limitCents?.let { it.toAmountText() } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidAmountMessage = stringResource(R.string.error_invalid_amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_dialog_title, budget.category.displayName)) },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    error = null
                },
                label = { Text(stringResource(R.string.budget_dialog_limit_label)) },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                parseAmountCents(amountText)
                    .onSuccess { cents -> onSave(cents) }
                    .onFailure { error = invalidAmountMessage }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private const val CENTS_PER_UNIT = 100

private fun Long.toAmountText(): String {
    val whole = this / CENTS_PER_UNIT
    val fraction = (this % CENTS_PER_UNIT).let { if (it < 0) -it else it }
    return "$whole.${fraction.toString().padStart(2, '0')}"
}
