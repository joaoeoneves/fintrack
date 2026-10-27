package com.joaoeoneves.fintrack.ui.income.list

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.toInstantRange
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
import com.joaoeoneves.fintrack.ui.expense.list.SortOption
import com.joaoeoneves.fintrack.ui.navigation.IncomeList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

private const val TAG = "IncomeListViewModel"

// How long (in ms) the underlying Firestore listener is kept alive after the last collector
// disappears, so a quick configuration change doesn't tear down and immediately re-establish it.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** The repository's entries for the selected period, before the query/filter/sort are applied. */
private sealed interface PeriodItems<out T> {
    data class Loaded<T>(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val items: List<T>,
    ) : PeriodItems<T>

    data class Failed(
        val message: UiText,
    ) : PeriodItems<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncomeListViewModel
    @Inject
    constructor(
        private val incomeRepository: IncomeRepository,
        private val savedStateHandle: SavedStateHandle,
        private val clock: Clock,
    ) : ViewModel() {
        // The route's own "period" argument is only the initial value; the user's later choices
        // are stored under a separate key so the route argument itself is never overwritten.
        private val selection =
            PeriodSelection(
                savedStateHandle = savedStateHandle,
                key = KEY_SELECTED_PERIOD,
                initialToken = savedStateHandle.toRoute<IncomeList>().period,
                clock = clock,
            )

        // Public so the screen can bind the search field's displayed value directly to this,
        // instead of to Content.query (which only updates after the derived uiState recomputes).
        // A controlled text field bound to the derived value can drop/reorder fast-typed characters.
        val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
        private val sortOption = savedStateHandle.getStateFlow(KEY_SORT_OPTION, SortOption.DATE_DESC)
        private val retryTrigger = MutableStateFlow(0)

        private val undoEvents = Channel<Income>(Channel.BUFFERED)
        val undoEvent: Flow<Income> = undoEvents.receiveAsFlow()

        // Only the period and the retry trigger drive the Firestore subscription; the query, filter
        // and sort are applied afterwards (below), so typing or changing them never re-subscribes.
        private val periodItems: Flow<PeriodItems<Income>> =
            combine(selection.token, retryTrigger) { token, _ -> token }
                .flatMapLatest { token ->
                    // "Now" is read on every (re)subscription so an ON_RESUME retry after the
                    // calendar rolls over re-resolves CurrentMonth and canGoToNextMonth.
                    val now = clock.instant()
                    val period = decodePeriod(token, now, clock.zone)
                    val canGoToNextMonth = period.canGoToNextMonth(now, clock.zone)
                    val instantRange = period.toInstantRange(now, clock.zone)
                    incomeRepository
                        .observeIncome(instantRange.startInclusive, instantRange.endExclusive)
                        .map<List<Income>, PeriodItems<Income>> { PeriodItems.Loaded(period, canGoToNextMonth, it) }
                        .catch { e ->
                            Log.w(TAG, "Income list flow failed", e)
                            emit(PeriodItems.Failed(UiText.StringResource(R.string.error_generic_fallback)))
                        }
                }

        val uiState: StateFlow<IncomeListUiState> =
            combine(periodItems, query, sortOption) { items, q, sort ->
                when (items) {
                    is PeriodItems.Loaded -> items.items.toListContent(items.period, items.canGoToNextMonth, q, sort)
                    is PeriodItems.Failed -> IncomeListUiState.Error(items.message)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = IncomeListUiState.Loading,
            )

        fun onPeriodSelected(period: Period) = selection.select(period)

        fun onPreviousMonth() = selection.previous()

        fun onNextMonth() = selection.next()

        fun onQueryChanged(newQuery: String) {
            savedStateHandle[KEY_QUERY] = newQuery
        }

        fun onSortSelected(sort: SortOption) {
            savedStateHandle[KEY_SORT_OPTION] = sort
        }

        fun onRetry() {
            retryTrigger.value++
        }

        fun onDeleteIncome(id: String) {
            viewModelScope.launch {
                val currentState = uiState.value
                val incomeToDelete =
                    (currentState as? IncomeListUiState.Content)
                        ?.income
                        ?.find { it.id == id }

                val result = incomeRepository.deleteIncome(id)

                if (result.isSuccess && incomeToDelete != null) {
                    undoEvents.send(incomeToDelete)
                }
            }
        }

        fun onUndoDelete(income: Income) {
            viewModelScope.launch {
                incomeRepository.addIncome(income)
            }
        }

        private companion object {
            private const val KEY_QUERY = "query"
            private const val KEY_SORT_OPTION = "sortOption"
            private const val KEY_SELECTED_PERIOD = "selectedPeriod"
        }
    }
