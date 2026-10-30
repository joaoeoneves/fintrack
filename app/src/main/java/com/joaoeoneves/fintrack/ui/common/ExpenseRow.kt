package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.ui.designsystem.TransactionRow
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.ZoneId

/**
 * A single expense row (name, category, amount, optionally date) shared between the dashboard's
 * recent-expense list and the full expense list. Set [showDate] to false under a day header.
 */
@Composable
fun ExpenseRow(
    expense: Expense,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDate: Boolean = true,
    /** How much of this expense was refunded (1..100, from linked refunds); `null` or 0 shows nothing. */
    refundedPercent: Int? = null,
) {
    val dateFormatter = rememberLocaleAwareDateFormatter()
    val subtitle =
        if (showDate) {
            stringResource(
                R.string.row_category_date,
                expense.category.displayName,
                expense.date.atZone(ZoneId.systemDefault()).format(dateFormatter),
            )
        } else {
            expense.category.displayName
        }
    // Refunds are money coming back, so they read like income: "+" and the positive color.
    val (sign, amountColor) =
        if (expense.isRefund) {
            "+" to FinTrackTheme.colors.positive
        } else {
            MINUS_SIGN to MaterialTheme.colorScheme.onSurface
        }
    TransactionRow(
        icon = expense.category.icon,
        title = expense.name,
        subtitle = subtitle,
        amountText = "$sign${formatAmountCents(expense.amountCents)}",
        amountColor = amountColor,
        onClick = onClick,
        modifier = modifier,
        badge =
            when {
                expense.isRefund -> ({ RefundBadge(stringResource(R.string.expense_refund_badge)) })
                refundedPercent != null && refundedPercent > 0 ->
                    ({ RefundBadge(stringResource(R.string.expense_refunded_badge, refundedPercent)) })
                else -> null
            },
    )
}

@Composable
private fun RefundBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier =
            modifier
                .background(color = FinTrackTheme.colors.surface2, shape = CircleShape)
                .padding(horizontal = Dimens.Space8, vertical = Dimens.Space4 / 2),
    )
}
