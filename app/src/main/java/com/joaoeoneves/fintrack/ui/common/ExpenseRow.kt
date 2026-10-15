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
        Text(
            text = "-${formatAmountCents(expense.amountCents)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
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
            Text(
                text = expense.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
