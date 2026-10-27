package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.ui.common.DaySectionHeader
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.IncomeRow
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.dayHeaderOf
import com.joaoeoneves.fintrack.ui.common.groupByDay
import com.joaoeoneves.fintrack.ui.designsystem.SectionAction
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.LocalDate
import java.time.ZoneId

/** The latest expenses of the period, grouped by day, under a title that opens the full list. */
@Composable
fun RecentExpensesSection(
    expenses: List<Expense>,
    period: Period,
    callbacks: DashboardCallbacks,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(expenses) { LocalDate.now(zone) }
    Column(modifier = modifier.fillMaxWidth()) {
        RecentHeader(
            title = stringResource(R.string.dashboard_recent_expenses),
            onOpenList = { callbacks.onOpenList(period) },
            seeAll =
                SeeAllLink(
                    description = stringResource(R.string.dashboard_see_all_expenses),
                    modifier = Modifier.testTag(TestTags.SEE_ALL_EXPENSES),
                ).takeIf { expenses.isNotEmpty() },
        )
        if (expenses.isEmpty()) {
            EmptyLine(stringResource(R.string.dashboard_recent_expenses_empty))
        } else {
            remember(expenses) { groupByDay(expenses, zone) }.forEach { section ->
                DaySectionHeader(dayHeaderOf(section.date, today))
                section.items.forEach { expense ->
                    ExpenseRow(expense = expense, showDate = false, onClick = { callbacks.onOpenExpense(expense.id) })
                }
            }
        }
    }
}

/** The latest income of the period, grouped by day, under a title that opens the full list. */
@Composable
fun RecentIncomeSection(
    income: List<Income>,
    period: Period,
    callbacks: DashboardCallbacks,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(income) { LocalDate.now(zone) }
    Column(modifier = modifier.fillMaxWidth()) {
        RecentHeader(
            title = stringResource(R.string.dashboard_recent_income),
            onOpenList = { callbacks.onOpenIncomeList(period) },
            seeAll =
                SeeAllLink(stringResource(R.string.dashboard_see_all_income)).takeIf { income.isNotEmpty() },
        )
        if (income.isEmpty()) {
            EmptyLine(stringResource(R.string.dashboard_no_income))
        } else {
            remember(income) { groupByDay(income, zone) }.forEach { section ->
                DaySectionHeader(dayHeaderOf(section.date, today))
                section.items.forEach { entry ->
                    IncomeRow(income = entry, showDate = false, onClick = { callbacks.onOpenIncome(entry.id) })
                }
            }
        }
    }
}

/** The trailing "See all" link: spoken as [description], with an optional [modifier] such as a test tag. */
private class SeeAllLink(
    val description: String,
    val modifier: Modifier = Modifier,
)

@Composable
private fun RecentHeader(
    title: String,
    onOpenList: () -> Unit,
    seeAll: SeeAllLink?,
    modifier: Modifier = Modifier,
) {
    SectionTitle(
        title = title,
        onTitleClick = onOpenList,
        modifier = modifier,
        trailing =
            seeAll?.let {
                {
                    SectionAction(
                        text = stringResource(R.string.action_see_all),
                        onClick = onOpenList,
                        contentDescription = it.description,
                        modifier = it.modifier,
                    )
                }
            },
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = FinTrackTheme.colors.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space8),
    )
}
