package com.joaoeoneves.fintrack.ui.common

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Expense
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A single expense row (name, category, amount, date) shared between the dashboard's recent-expense
 * list and the full expense list.
 */
@Composable
fun ExpenseRow(
    expense: Expense,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val dateFormatter = rememberLocaleAwareDateFormatter()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpenseRowLeading(expense = expense, dateFormatter = dateFormatter, modifier = Modifier.weight(1f))
        // Refunds are money coming back, so they read like income: "+" and the primary color.
        val (sign, amountColor) =
            if (expense.isRefund) {
                "+" to MaterialTheme.colorScheme.primary
            } else {
                "-" to MaterialTheme.colorScheme.onSurface
            }
        Text(
            text = "$sign${formatAmountCents(expense.amountCents)}",
            style = MaterialTheme.typography.bodyLarge,
            color = amountColor,
        )
    }
}

@Composable
private fun ExpenseRowLeading(
    expense: Expense,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(expense.category.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = expense.category.icon,
                contentDescription = null,
                tint = expense.category.color,
            )
        }
        Column(
            modifier =
                Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = expense.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (expense.isRefund) {
                    RefundBadge(modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(
                text =
                    stringResource(
                        R.string.row_category_date,
                        expense.category.displayName,
                        expense.date.atZone(ZoneId.systemDefault()).format(dateFormatter),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RefundBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.expense_refund_badge),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 1,
        modifier =
            modifier
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp),
                ).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
