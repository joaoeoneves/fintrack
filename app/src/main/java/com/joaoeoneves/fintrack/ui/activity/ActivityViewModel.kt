package com.joaoeoneves.fintrack.ui.activity

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.netCents
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.TransactionRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.groupByDay
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
import com.joaoeoneves.fintrack.ui.common.period.asMonthPeriod
import com.joaoeoneves.fintrack.ui.navigation.ACTIVITY_TYPE_INCOME
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

// The Activity route's arguments, handed over through the SavedStateHandle: only initial values.
private const val ARG_PERIOD = "period"
private const val ARG_TYPE = "type"
private const val ARG_CATEGORY = "category"

private const val ROUTE_TYPE_EXPENSE = "EXPENSE"

private const val TAG = "ActivityViewModel"

// How long (in ms) the underlying Firestore listeners are kept alive after the last collector
// disappears, so a quick configuration change doesn't tear down and immediately re-establish them.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** The repositories' transactions for the selected period, before the filters and sort are applied. */
private sealed interface PeriodItems {
    data class Loaded(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val transactions: TransactionsInRange,
    ) : PeriodItems

    data class Failed(
        val message: UiText,
    ) : PeriodItems
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel
    @Inject
    constructor(
        transactionRepository: TransactionRepository,
        categoryRepository: CategoryRepository,
        private val expenseRepository: ExpenseRepository,
        private val incomeRepository: IncomeRepository,
        private val savedStateHandle: SavedStateHandle,
        private val clock: Clock,
    ) : ViewModel() {
        // The route's own arguments are only initial values; the user's later choices are stored
        // under separate keys so the route arguments themselves are never overwritten.
        private val selection =
            PeriodSelection(
                savedStateHandle = savedStateHandle,
                key = KEY_SELECTED_PERIOD,
                initialToken = initialPeriodToken(),
                clock = clock,
            )

        // Public so the screen can bind the search field's displayed value directly to this,
        // instead of to Content.filters.query (which only updates after the derived uiState
        // recomputes). A controlled text field bound to the derived value can drop/reorder
        // fast-typed characters.
        val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
        private val sortOption = savedStateHandle.getStateFlow(KEY_SORT_OPTION, SortOption.DATE_DESC)
        private val retryTrigger = MutableStateFlow(0)

        // Persisted as the enum itself; the route's token only seeds the first value.
        private val selectedType =
            savedStateHandle.getStateFlow(KEY_SELECTED_TYPE, initialType(savedStateHandle.get<String>(ARG_TYPE)))
        val type: StateFlow<ActivityType> = selectedType

        // Persisted in the SavedStateHandle as the id's raw String (a value class isn't Bundle-able).
        private val selectedCategory =
            MutableStateFlow(
                // A stored null means "cleared by the user", so only fall back to the route when never chosen.
                (
                    if (savedStateHandle.contains(KEY_SELECTED_CATEGORY)) {
                        savedStateHandle.get<String>(KEY_SELECTED_CATEGORY)
                    } else {
                        savedStateHandle.get<String>(ARG_CATEGORY)
                    }
                )?.let(::CategoryId),
            )
        val categoryFilter: StateFlow<CategoryId?> = selectedCategory.asStateFlow()

        private val undoEvents = Channel<Transaction>(Channel.BUFFERED)
        val undoEvent: Flow<Transaction> = undoEvents.receiveAsFlow()

        /** The full category catalog (archived included), for the filter chips and labels. */
        val categories: StateFlow<List<Category>> =
            categoryRepository
                .observeCategories()
                .catch { emit(BuiltInCategories.all) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), BuiltInCategories.all)

        // Localized category labels the query is also matched against; pushed in by the screen.
        private val categoryLabels = MutableStateFlow<Map<CategoryId, String>>(emptyMap())

        // Only the period and the retry trigger drive the repositories' subscription; the filters
        // and sort are applied afterwards (below), so typing or changing them never re-subscribes.
        private val periodItems: Flow<PeriodItems> =
            combine(selection.token, retryTrigger) { token, _ -> token }
                .flatMapLatest { token ->
                    // "Now" is read on every (re)subscription so an ON_RESUME retry after the
                    // calendar rolls over re-resolves CurrentMonth and canGoToNextMonth.
                    val now = clock.instant()
                    val period = decodePeriod(token, now, clock.zone)
                    val canGoToNextMonth = period.canGoToNextMonth(now, clock.zone)
                    transactionRepository
                        .observeTransactions(period.toInstantRange(now, clock.zone))
                        .map<TransactionsInRange, PeriodItems> { PeriodItems.Loaded(period, canGoToNextMonth, it) }
                        .catch { e ->
                            Log.w(TAG, "Activity flow failed", e)
                            emit(PeriodItems.Failed(UiText.StringResource(R.string.error_generic_fallback)))
                        }
                }

        private val filters: Flow<ActivityFilters> =
            combine(selectedType, selectedCategory, query, sortOption) { type, category, query, sort ->
                ActivityFilters(type = type, category = category, query = query, sort = sort)
            }

        /** Period navigation for the selector; the list is month-only, so any other period snaps to its month. */
        val periodActions =
            PeriodActions(
                onPrevious = selection::previous,
                onNext = selection::next,
                onSelect = { selection.select(it.asMonthPeriod()) },
            )

        val uiState: StateFlow<ActivityUiState> =
            combine(periodItems, filters, categoryLabels) { items, activeFilters, labels ->
                when (items) {
                    is PeriodItems.Loaded -> items.toContent(activeFilters, labels, clock.zone)
                    is PeriodItems.Failed -> ActivityUiState.Error(items.message)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = ActivityUiState.Loading,
            )

        fun onTypeSelected(type: ActivityType) {
            savedStateHandle[KEY_SELECTED_TYPE] = type
        }

        fun onQueryChanged(newQuery: String) {
            savedStateHandle[KEY_QUERY] = newQuery
        }

        fun onCategorySelected(id: CategoryId?) {
            savedStateHandle[KEY_SELECTED_CATEGORY] = id?.value
            selectedCategory.value = id
        }

        fun onCategoryLabelsChanged(labels: Map<CategoryId, String>) {
            categoryLabels.value = labels
        }

        fun onSortSelected(sort: SortOption) {
            savedStateHandle[KEY_SORT_OPTION] = sort
        }

        fun onRetry() {
            retryTrigger.value++
        }

        /** Deletes the expense or income entry with this [id] (whichever the list holds) and offers an undo. */
        fun onDelete(id: String) {
            viewModelScope.launch {
                val transaction =
                    (uiState.value as? ActivityUiState.Content)?.items?.find { it.id == id } ?: return@launch
                val result =
                    when (transaction) {
                        is Expense -> expenseRepository.deleteExpense(transaction.id)
                        is Income -> incomeRepository.deleteIncome(transaction.id)
                    }
                if (result.isSuccess) {
                    undoEvents.send(transaction)
                }
            }
        }

        /** Re-adds a deleted [transaction] under its original id. */
        fun onUndoDelete(transaction: Transaction) {
            viewModelScope.launch {
                when (transaction) {
                    is Expense -> expenseRepository.addExpense(transaction)
                    is Income -> incomeRepository.addIncome(transaction)
                }
            }
        }

        private fun initialPeriodToken(): String {
            val now = clock.instant()
            val routePeriod = decodePeriod(savedStateHandle.get<String>(ARG_PERIOD), now, clock.zone)
            return routePeriod.asMonthPeriod().encode()
        }

        private companion object {
            private const val KEY_QUERY = "query"
            private const val KEY_SORT_OPTION = "sortOption"
            private const val KEY_SELECTED_CATEGORY = "selectedCategory"
            private const val KEY_SELECTED_TYPE = "selectedType"
            private const val KEY_SELECTED_PERIOD = "selectedPeriod"
        }
    }

private fun initialType(routeType: String?): ActivityType =
    when (routeType) {
        ROUTE_TYPE_EXPENSE -> ActivityType.EXPENSE
        ACTIVITY_TYPE_INCOME -> ActivityType.INCOME
        else -> ActivityType.ALL
    }

private fun PeriodItems.Loaded.toContent(
    activeFilters: ActivityFilters,
    labels: Map<CategoryId, String>,
    zone: ZoneId,
): ActivityUiState.Content {
    val filtered =
        filterTransactions(
            list = transactions.expenses + transactions.income,
            type = activeFilters.type,
            category = activeFilters.category,
            query = activeFilters.query,
            labels = labels,
        )
    val sorted = sortTransactions(filtered, activeFilters.sort)
    return ActivityUiState.Content(
        period = period,
        canGoToNextMonth = canGoToNextMonth,
        items = sorted,
        days = if (shouldGroupByDay(activeFilters.sort)) groupByDay(sorted, zone) else emptyList(),
        totals =
            ActivityTotals(
                inCents = transactions.income.sumOf { it.amountCents },
                outCents = transactions.expenses.netCents(),
            ),
        filters = activeFilters,
    )
}
