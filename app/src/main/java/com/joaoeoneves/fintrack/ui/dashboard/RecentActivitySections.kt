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
import com.joaoeoneves.fintrack.domain.model.Transaction
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

/**
 * The latest expenses and income of the period merged into one date-sorted list, grouped by day,
 * under a title and a "See all" link that open the Activity tab on [period].
 */
@Composable
fun RecentActivitySection(
    items: List<Transaction>,
    period: Period,
    callbacks: DashboardCallbacks,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(items) { LocalDate.now(zone) }
    val onSeeAll = { callbacks.onOpenActivity(period) }
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(
            title = stringResource(R.string.home_recent_title),
            onTitleClick = onSeeAll,
            trailing =
                if (items.isEmpty()) {
                    null
                } else {
                    {
                        SectionAction(
                            text = stringResource(R.string.action_see_all),
                            onClick = onSeeAll,
                            contentDescription = stringResource(R.string.dashboard_see_all_activity),
                            modifier = Modifier.testTag(TestTags.SEE_ALL_ACTIVITY),
                        )
                    }
                },
        )
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.home_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = FinTrackTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space8),
            )
        } else {
            remember(items) { groupByDay(items, zone) }.forEach { section ->
                DaySectionHeader(dayHeaderOf(section.date, today))
                section.items.forEach { item -> RecentRow(item, callbacks) }
            }
        }
    }
}

@Composable
private fun RecentRow(
    item: Transaction,
    callbacks: DashboardCallbacks,
) {
    when (item) {
        is Expense -> ExpenseRow(expense = item, showDate = false, onClick = { callbacks.onOpenExpense(item.id) })
        is Income -> IncomeRow(income = item, showDate = false, onClick = { callbacks.onOpenIncome(item.id) })
    }
}
