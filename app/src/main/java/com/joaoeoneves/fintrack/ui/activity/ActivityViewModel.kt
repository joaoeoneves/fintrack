package com.joaoeoneves.fintrack.ui.activity

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PeriodOverview
import com.joaoeoneves.fintrack.domain.model.TransactionsInRange
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.model.incomeSourceOf
import com.joaoeoneves.fintrack.domain.model.netCents
import com.joaoeoneves.fintrack.domain.model.refundIndexOf
import com.joaoeoneves.fintrack.domain.model.resolve
import com.joaoeoneves.fintrack.domain.model.spendingSplitOf
import com.joaoeoneves.fintrack.domain.model.usesMealCard
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import com.joaoeoneves.fintrack.domain.usecase.BulkEditExpenses
import com.joaoeoneves.fintrack.domain.usecase.BulkKind
import com.joaoeoneves.fintrack.domain.usecase.BulkUndo
import com.joaoeoneves.fintrack.domain.usecase.DeleteTransaction
import com.joaoeoneves.fintrack.domain.usecase.DeletedTransaction
import com.joaoeoneves.fintrack.domain.usecase.ObservePeriodOverview
import com.joaoeoneves.fintrack.ui.activity.analytics.ActivityAnalytics
import com.joaoeoneves.fintrack.ui.activity.analytics.NO_ENVELOPE
import com.joaoeoneves.fintrack.ui.activity.analytics.analyticsOf
import com.joaoeoneves.fintrack.ui.activity.analytics.encode
import com.joaoeoneves.fintrack.ui.activity.analytics.periodIncomeSourcesOf
import com.joaoeoneves.fintrack.ui.activity.analytics.periodMonthsOf
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.groupByDay
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
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
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

// The Activity route's arguments, handed over through the SavedStateHandle: only initial values.
private const val ARG_PERIOD = "period"
private const val ARG_TYPE = "type"
private const val ARG_CATEGORY = "category"
private const val ARG_ENVELOPE = "envelope"

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
        val overview: PeriodOverview,
        val months: List<YearMonth>,
    ) : PeriodItems

    data class Failed(
        val message: UiText,
    ) : PeriodItems
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "LongParameterList") // One handler per user action; injected collaborators.
@HiltViewModel
class ActivityViewModel
    @Inject
    constructor(
        private val observePeriodOverview: ObservePeriodOverview,
        categoryRepository: CategoryRepository,
        private val deleteTransaction: DeleteTransaction,
        expenseRepository: ExpenseRepository,
        private val bulkEditExpenses: BulkEditExpenses,
        salaryProfiles: SalaryProfileRepository,
        private val savedStateHandle: SavedStateHandle,
        private val clock: Clock,
    ) : ViewModel() {
        // The route's own arguments are only initial values; the user's later choices are stored
        // under separate keys so the route arguments themselves are never overwritten.
        private val selection =
            PeriodSelection(
                savedStateHandle = savedStateHandle,
                key = KEY_SELECTED_PERIOD,
                initialToken = savedStateHandle.get<String>(ARG_PERIOD) ?: Period.CurrentMonth.encode(),
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
            savedStateHandle.getStateFlow(
                KEY_SELECTED_TYPE,
                initialType(
                    routeType = savedStateHandle.get<String>(ARG_TYPE),
                    // A category or envelope handed over by the route lives on the Expenses tab.
                    hasExpenseFilter =
                        savedStateHandle.contains(ARG_CATEGORY) || savedStateHandle.contains(ARG_ENVELOPE),
                ),
            )
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

        // The envelope id (NO_ENVELOPE for "no envelope"); like the category, a stored null means "cleared".
        private val selectedEnvelope =
            MutableStateFlow(
                if (savedStateHandle.contains(KEY_SELECTED_ENVELOPE)) {
                    savedStateHandle.get<String>(KEY_SELECTED_ENVELOPE)
                } else {
                    savedStateHandle.get<String>(ARG_ENVELOPE)
                },
            )
        val envelopeFilter: StateFlow<String?> = selectedEnvelope.asStateFlow()

        // The selected income source (an encoded IncomeSourceKey) on the Income tab; null shows every source.
        private val selectedIncomeSource = savedStateHandle.getStateFlow<String?>(KEY_SELECTED_INCOME_SOURCE, null)

        private val undoEvents = Channel<DeletedTransaction>(Channel.BUFFERED)
        val undoEvent: Flow<DeletedTransaction> = undoEvents.receiveAsFlow()

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
                    observePeriodOverview(period, now, clock.zone, recentLimit = 0)
                        .map<PeriodOverview, PeriodItems> {
                            PeriodItems.Loaded(
                                period,
                                canGoToNextMonth,
                                it.transactions,
                                it,
                                periodMonthsOf(period, now, clock.zone),
                            )
                        }.catch { e ->
                            Log.w(TAG, "Activity flow failed", e)
                            emit(PeriodItems.Failed(UiText.StringResource(R.string.error_generic_fallback)))
                        }
                }

        private val filters: Flow<ActivityFilters> =
            combine(
                combine(selectedType, selectedCategory, selectedEnvelope) { type, category, envelope ->
                    Triple(type, category, envelope)
                },
                combine(query, sortOption, selectedIncomeSource) { query, sort, source -> Triple(query, sort, source) },
            ) { (type, category, envelope), (query, sort, source) ->
                ActivityFilters(
                    type = type,
                    category = category,
                    envelope = envelope,
                    query = query,
                    sort = sort,
                    incomeSource = source,
                )
            }

        /** Period navigation for the selector; any period kind (month, rolling, custom) is allowed. */
        val periodActions =
            PeriodActions(
                onPrevious = { onClearSelection().also { selection.previous() } },
                onNext = { onClearSelection().also { selection.next() } },
                onSelect = { onClearSelection().also { _ -> selection.select(it) } },
                onSelectScale = { onClearSelection().also { _ -> selection.selectScale(it) } },
            )

        // The ids of the multi-selected expenses (a plain list in the SavedStateHandle so it survives the process).
        val selectedIds: StateFlow<Set<String>> =
            savedStateHandle
                .getStateFlow(KEY_SELECTED_IDS, emptyList<String>())
                .map { it.toSet() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

        private val mealCardAvailable: StateFlow<Boolean> =
            salaryProfiles
                .observeSalaryProfile()
                .map { it.usesMealCard() }
                .catch { emit(false) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), false)

        private val bulkEvents = Channel<BulkEvent>(Channel.BUFFERED)

        /** One per finished bulk action, so the screen can show its snackbar with an undo. */
        val bulkUndoEvent: Flow<BulkEvent> = bulkEvents.receiveAsFlow()

        // Refunded cents per original expense id; a failing listener just shows no badges.
        private val refundIndex: Flow<Map<String, Long>> =
            expenseRepository
                .observeLinkedRefunds()
                .map { refundIndexOf(it) }
                .catch {
                    Log.w(TAG, "Linked refunds flow failed", it)
                    emit(emptyMap())
                }

        val uiState: StateFlow<ActivityUiState> =
            combine(
                periodItems,
                filters,
                categoryLabels,
                categories,
                refundIndex,
            ) { items, active, labels, catalog, refunds ->
                when (items) {
                    is PeriodItems.Loaded -> items.toContent(active, labels, catalog, clock.zone, refunds)
                    is PeriodItems.Failed -> ActivityUiState.Error(items.message)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = ActivityUiState.Loading,
            )

        /** The multi-selection as the bar shows it; `null` while nothing (still visible) is selected. */
        val selectionState: StateFlow<SelectionState?> =
            combine(selectedIds, uiState, mealCardAvailable) { ids, state, mealAvailable ->
                val chosen = (state as? ActivityUiState.Content)?.selectedExpenses(ids).orEmpty()
                chosen.takeIf { it.isNotEmpty() }?.let {
                    SelectionState(
                        count = it.size,
                        showMealCard = mealAvailable || it.any { e -> e.paidWithMealCard },
                        allOnMealCard = it.all { e -> e.paidWithMealCard },
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), null)

        fun onTypeSelected(type: ActivityType) {
            if (type != selectedType.value) onClearSelection()
            savedStateHandle[KEY_SELECTED_TYPE] = type
        }

        /** Starts a multi-selection with the expense [id] (incomes cannot be selected). */
        fun onLongPress(id: String) {
            if (selectedIds.value.isEmpty() && currentExpenses().any { it.id == id }) {
                savedStateHandle[KEY_SELECTED_IDS] = listOf(id)
            }
        }

        fun onToggleSelected(id: String) {
            val current = selectedIds.value
            if (current.isEmpty() || currentExpenses().none { it.id == id }) return
            savedStateHandle[KEY_SELECTED_IDS] = (if (id in current) current - id else current + id).toList()
        }

        fun onClearSelection() {
            if (savedStateHandle.get<List<String>>(KEY_SELECTED_IDS).orEmpty().isNotEmpty()) {
                savedStateHandle[KEY_SELECTED_IDS] = emptyList<String>()
            }
        }

        fun onBulkCategory(category: Category) = bulk { bulkEditExpenses.changeCategory(it, category) }

        /** Turns the meal card on for the selection, or off when every selected expense already has it. */
        fun onBulkMealCard() = bulk { bulkEditExpenses.setMealCard(it, enabled = !it.all { e -> e.paidWithMealCard }) }

        fun onBulkDelete() = bulk { bulkEditExpenses.delete(it) }

        fun onBulkUndo(undo: BulkUndo) {
            viewModelScope.launch { bulkEditExpenses.undo(undo) }
        }

        private fun currentExpenses(): List<Expense> =
            (uiState.value as? ActivityUiState.Content)?.items?.filterIsInstance<Expense>().orEmpty()

        private fun bulk(action: suspend (List<Expense>) -> Result<BulkUndo>) {
            val chosen = (uiState.value as? ActivityUiState.Content)?.selectedExpenses(selectedIds.value).orEmpty()
            if (chosen.isEmpty()) return
            viewModelScope.launch {
                action(chosen)
                    .onSuccess {
                        onClearSelection()
                        bulkEvents.send(BulkEvent(it.kind, chosen.size, it))
                    }.onFailure { Log.w(TAG, "Bulk edit failed", it) }
            }
        }

        fun onQueryChanged(newQuery: String) {
            savedStateHandle[KEY_QUERY] = newQuery
        }

        fun onCategorySelected(id: CategoryId?) {
            savedStateHandle[KEY_SELECTED_CATEGORY] = id?.value
            selectedCategory.value = id
            // Categories only exist on the Expenses tab: picking one from elsewhere (the breakdown) goes there.
            if (id != null) onTypeSelected(ActivityType.EXPENSE)
        }

        /** Selects an envelope (or [NO_ENVELOPE]; `null` clears); a selected category outside it is cleared too. */
        fun onEnvelopeSelected(id: String?) {
            savedStateHandle[KEY_SELECTED_ENVELOPE] = id
            selectedEnvelope.value = id
            if (id != null) onTypeSelected(ActivityType.EXPENSE)
            val category = selectedCategory.value?.let { selected -> categories.value.find { it.id == selected } }
            if (id != null && category != null) {
                val envelopeIds = (uiState.value as? ActivityUiState.Content)?.analytics?.envelopeIds.orEmpty()
                if ((envelopeIdOf(category, envelopeIds) ?: NO_ENVELOPE) != id) onCategorySelected(null)
            }
        }

        /** Selects an income source (its encoded key; `null` clears); the list and the Evolution chart follow it. */
        fun onIncomeSourceSelected(key: String?) {
            savedStateHandle[KEY_SELECTED_INCOME_SOURCE] = key
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
                deleteTransaction(transaction).onSuccess { undoEvents.send(it) }
            }
        }

        /** Restores the [deleted] transaction under its original id (and un-skips the occurrence it skipped). */
        fun onUndoDelete(deleted: DeletedTransaction) {
            viewModelScope.launch { deleteTransaction.undo(deleted) }
        }

        private companion object {
            private const val KEY_QUERY = "query"
            private const val KEY_SORT_OPTION = "sortOption"
            private const val KEY_SELECTED_CATEGORY = "selectedCategory"
            private const val KEY_SELECTED_TYPE = "selectedType"
            private const val KEY_SELECTED_PERIOD = "selectedPeriod"
            private const val KEY_SELECTED_ENVELOPE = "selectedEnvelope"
            private const val KEY_SELECTED_INCOME_SOURCE = "selectedIncomeSource"
            private const val KEY_SELECTED_IDS = "selectedIds"
        }
    }

private fun initialType(
    routeType: String?,
    hasExpenseFilter: Boolean = false,
): ActivityType =
    when {
        routeType == ROUTE_TYPE_EXPENSE -> ActivityType.EXPENSE
        routeType == ACTIVITY_TYPE_INCOME -> ActivityType.INCOME
        hasExpenseFilter -> ActivityType.EXPENSE
        else -> ActivityType.ALL
    }

/** The envelope [category] belongs to, only when that envelope still exists in [envelopeIds]. */
internal fun envelopeIdOf(
    category: Category,
    envelopeIds: Set<String>,
): String? = category.groupId?.takeIf { it in envelopeIds }

private fun PeriodItems.Loaded.toContent(
    requested: ActivityFilters,
    labels: Map<CategoryId, String>,
    catalog: List<Category>,
    zone: ZoneId,
    refunds: Map<String, Long>,
): ActivityUiState.Content {
    val analytics = overview.analyticsOf(requested.envelope, catalog)
    // An envelope id that no longer exists filters nothing.
    val envelope = requested.envelope?.takeIf { it == NO_ENVELOPE || it in analytics.envelopeIds }
    val activeFilters = requested.copy(envelope = envelope)
    val byId = catalog.associateBy { it.id }
    val lines = overview.plannedIncome
    val sources = periodIncomeSourcesOf(transactions.income, lines)
    // A source that is not in the period filters nothing.
    val incomeSource = requested.incomeSource?.takeIf { key -> sources.any { it.key == key } }
    val byType =
        filterTransactions(
            list = transactions.expenses + transactions.income,
            type = activeFilters.type,
            category = activeFilters.category,
            query = activeFilters.query,
            labels = labels,
            envelope = envelope,
            envelopeOf = { id -> byId[id]?.let { envelopeIdOf(it, analytics.envelopeIds) } },
        )
    val filtered =
        if (activeFilters.type == ActivityType.INCOME && incomeSource != null) {
            byType.filter { it !is Income || incomeSourceOf(it, lines).encode() == incomeSource }
        } else {
            byType
        }
    val sorted = sortTransactions(filtered, activeFilters.sort)
    return ActivityUiState.Content(
        period = period,
        canGoToNextMonth = canGoToNextMonth,
        items = sorted,
        days = if (shouldGroupByDay(activeFilters.sort)) groupByDay(sorted, zone) else emptyList(),
        filters = activeFilters.copy(incomeSource = incomeSource),
        analytics = analytics.following(sorted.filterIsInstance<Expense>(), catalog),
        incomeSources = sources,
        refundedPercent = refundedPercentOf(sorted.filterIsInstance<Expense>(), refunds),
        categoryScope =
            envelope?.let { selected ->
                catalog
                    .filter { (envelopeIdOf(it, analytics.envelopeIds) ?: NO_ENVELOPE) == selected }
                    .map { it.id }
                    .toSet()
            },
    )
}

/**
 * The header's "Spent", fixed/variable split and per-category breakdown totals follow the visible [expenses]
 * (the planned slices and the per-envelope view stay whole-period).
 */
private fun ActivityAnalytics.following(
    expenses: List<Expense>,
    catalog: List<Category>,
): ActivityAnalytics {
    val spending = expenses.filter { catalog.resolve(it.category.id).kind == CategoryKind.SPENDING }
    val visibleSplit = spendingSplitOf(spending)
    val visibleByCategory = expenses.groupBy { it.category.id }
    val visibleTotals =
        breakdown.categoryTotals.map { total ->
            total.copy(totalCents = visibleByCategory[total.category.id].orEmpty().netCents())
        }
    return copy(
        spentCents = visibleSplit.spendingCents,
        split = visibleSplit,
        breakdown = breakdown.copy(categoryTotals = visibleTotals),
    )
}

/** The share (1..100, floored, capped) of each non-refund expense in [expenses] that [refunds] returned. */
private fun refundedPercentOf(
    expenses: List<Expense>,
    refunds: Map<String, Long>,
): Map<String, Int> =
    expenses
        .filter { !it.isRefund && it.amountCents > 0L }
        .mapNotNull { expense ->
            val refunded = refunds[expense.id] ?: return@mapNotNull null
            val percent = (refunded * PERCENT / expense.amountCents).toInt().coerceAtMost(PERCENT)
            if (percent > 0) expense.id to percent else null
        }.toMap()

private const val PERCENT = 100

/** What the selection bar shows: how many expenses, and whether/how the meal-card action applies. */
data class SelectionState(
    val count: Int,
    val showMealCard: Boolean,
    val allOnMealCard: Boolean,
)

/** A finished bulk action: what it did, to how many expenses, and the [undo] that reverts it. */
data class BulkEvent(
    val kind: BulkKind,
    val count: Int,
    val undo: BulkUndo,
)

private fun ActivityUiState.Content.selectedExpenses(ids: Set<String>): List<Expense> =
    items.filterIsInstance<Expense>().filter { it.id in ids }
