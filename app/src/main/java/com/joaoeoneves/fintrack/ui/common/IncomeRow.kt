package com.joaoeoneves.fintrack.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.ui.activity.analytics.monthName
import com.joaoeoneves.fintrack.ui.designsystem.TransactionRow
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.YearMonth
import java.time.ZoneId

/**
 * A single income row (source, amount, optionally date) shared between the dashboard's
 * recent-income list and the full income list. Under a day header ([showDate] false) the subtitle
 * falls back to the note.
 */
@Composable
fun IncomeRow(
    income: Income,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDate: Boolean = true,
    countsFor: YearMonth? = null,
) {
    val dateFormatter = rememberLocaleAwareDateFormatter()
    TransactionRow(
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        title = income.source,
        subtitle =
            listOfNotNull(
                if (showDate) {
                    income.date.atZone(ZoneId.systemDefault()).format(dateFormatter)
                } else {
                    income.note.orEmpty().takeIf { it.isNotEmpty() }
                },
                countsFor?.let { stringResource(R.string.activity_income_counts_for, monthName(it, currentLocale())) },
            ).joinToString(" · "),
        amountText = "+${formatAmountCents(income.amountCents)}",
        amountColor = FinTrackTheme.colors.positive,
        onClick = onClick,
        modifier = modifier,
    )
}
