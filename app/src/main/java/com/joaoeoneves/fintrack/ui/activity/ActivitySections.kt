package com.joaoeoneves.fintrack.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.domain.model.optionsIncluding
import com.joaoeoneves.fintrack.domain.model.resolve
import com.joaoeoneves.fintrack.ui.common.DaySectionHeader
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.IncomeRow
import com.joaoeoneves.fintrack.ui.common.SearchField
import com.joaoeoneves.fintrack.ui.common.SortDropdown
import com.joaoeoneves.fintrack.ui.common.SwipeToDeleteRow
import com.joaoeoneves.fintrack.ui.common.dayHeaderOf
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelector
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.LocalDate
import java.time.ZoneId

/** Period selector, totals, type chips, category chips (not for income) and the search and sort row. */
fun LazyListScope.activityHeaderItems(
    state: ActivityUiState.Content,
    query: String,
    categories: List<Category>,
    actions: ActivityActions,
) {
    item(key = "period") {
        PeriodSelector(
            period = state.period,
            canGoToNextMonth = state.canGoToNextMonth,
            actions = actions.period,
            alignment = Alignment.Start,
            monthOnly = true,
            modifier = Modifier.padding(horizontal = Dimens.Space8),
        )
    }
    item(key = "totals") { ActivityTotalsRow(state.totals) }
    item(key = "type") {
        SegmentedTextTabs(
            options = ActivityType.entries,
            selected = state.filters.type,
            label = { stringResource(it.labelRes) },
            onSelect = actions.onTypeSelected,
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
        )
    }
    if (state.filters.type != ActivityType.INCOME) {
        item(key = "categories") {
            val selected = state.filters.category
            CategoryFilterRow(
                // Archived categories are hidden unless one is the active filter.
                categories = categories.optionsIncluding(selected?.let(categories::resolve)),
                selected = selected,
                onSelected = actions.onCategorySelected,
                modifier = Modifier.padding(top = Dimens.Space8),
            )
        }
    }
    item(key = "search") {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query = query,
                onQueryChanged = actions.onQueryChanged,
                label = stringResource(R.string.activity_search_label),
                modifier = Modifier.weight(1f),
            )
            SortDropdown(
                options = SortOption.entries,
                selected = state.filters.sort,
                onSelected = actions.onSortSelected,
                optionLabel = { it.label },
            )
        }
    }
}

/** The transaction rows, under day headers for date sorts and flat for amount sorts; an empty state if none. */
fun LazyListScope.activityRowItems(
    state: ActivityUiState.Content,
    actions: ActivityActions,
) {
    if (state.items.isEmpty()) {
        item(key = "empty") {
            EmptyState(title = emptyTitle(state.filters), modifier = Modifier.fillMaxWidth())
        }
        return
    }
    if (state.days.isEmpty()) {
        items(state.items, key = { it.rowKey() }) { transaction ->
            ActivityRow(transaction, showDate = true, actions = actions)
        }
        return
    }
    state.days.forEach { section ->
        item(key = "day_${section.date}") {
            val today = remember(state.items) { LocalDate.now(ZoneId.systemDefault()) }
            DaySectionHeader(
                header = dayHeaderOf(section.date, today),
                modifier = Modifier.animateItem().padding(horizontal = Dimens.ScreenPadding),
            )
        }
        items(section.items, key = { it.rowKey() }) { transaction ->
            ActivityRow(transaction, showDate = false, actions = actions)
        }
    }
}

@Composable
private fun ActivityTotalsRow(
    totals: ActivityTotals,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space24),
    ) {
        TotalCell(
            label = stringResource(R.string.activity_total_in),
            text = "+${formatAmountCents(totals.inCents)}",
            color = FinTrackTheme.colors.positive,
        )
        TotalCell(
            label = stringResource(R.string.activity_total_out),
            text = formatAmountCents(totals.outCents),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TotalCell(
    label: String,
    text: String,
    color: Color,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = FinTrackTheme.colors.textSecondary)
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun LazyItemScope.ActivityRow(
    transaction: Transaction,
    showDate: Boolean,
    actions: ActivityActions,
) {
    SwipeToDeleteRow(
        onDelete = { actions.onDelete(transaction.id) },
        deleteLabel = stringResource(R.string.cd_delete),
        modifier = Modifier.animateItem(),
    ) {
        val rowModifier = Modifier.weight(1f).padding(horizontal = Dimens.ScreenPadding)
        when (transaction) {
            is Expense ->
                ExpenseRow(
                    expense = transaction,
                    showDate = showDate,
                    onClick = { actions.onOpen(transaction) },
                    modifier = rowModifier,
                )
            is Income ->
                IncomeRow(
                    income = transaction,
                    showDate = showDate,
                    onClick = { actions.onOpen(transaction) },
                    modifier = rowModifier,
                )
        }
    }
}

// Expense and income ids come from different collections, so the row key carries the kind.
private fun Transaction.rowKey(): String =
    when (this) {
        is Expense -> "expense_$id"
        is Income -> "income_$id"
    }

@Composable
private fun emptyTitle(filters: ActivityFilters): String =
    when {
        filters.query.isNotBlank() -> stringResource(R.string.activity_no_match, filters.query)
        filters.category != null && filters.type != ActivityType.INCOME ->
            stringResource(R.string.activity_no_match_filter)
        filters.type == ActivityType.EXPENSE -> stringResource(R.string.dashboard_no_expenses)
        filters.type == ActivityType.INCOME -> stringResource(R.string.dashboard_no_income)
        else -> stringResource(R.string.activity_empty)
    }

private val ActivityType.labelRes: Int
    get() =
        when (this) {
            ActivityType.ALL -> R.string.activity_type_all
            ActivityType.EXPENSE -> R.string.dashboard_expenses_label
            ActivityType.INCOME -> R.string.dashboard_income_label
        }

private val SortOption.label: String
    @Composable
    get() =
        when (this) {
            SortOption.DATE_DESC -> stringResource(R.string.sort_newest_first)
            SortOption.DATE_ASC -> stringResource(R.string.sort_oldest_first)
            SortOption.AMOUNT_DESC -> stringResource(R.string.sort_highest_amount)
            SortOption.AMOUNT_ASC -> stringResource(R.string.sort_lowest_amount)
        }
