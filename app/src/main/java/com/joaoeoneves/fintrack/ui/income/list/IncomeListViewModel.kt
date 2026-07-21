package com.joaoeoneves.fintrack.ui.income.list

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.ui.navigation.IncomeList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.time.Instant
import javax.inject.Inject

// How long (in ms) the underlying Firestore listener is kept alive after the last collector
// disappears, so a quick configuration change doesn't tear down and immediately re-establish it.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncomeListViewModel
    @Inject
    constructor(
        private val incomeRepository: IncomeRepository,
        private val savedStateHandle: SavedStateHandle,
        // Nullable with a default so existing unit tests that construct this ViewModel directly
        // (bypassing Hilt) keep compiling; Hilt itself always supplies a real ApplicationContext in
        // production. When null (test-only), the fallback below matches the exact literal those
        // tests assert on.
        @param:ApplicationContext private val context: Context? = null,
    ) : ViewModel() {
        private val timeRange = MutableStateFlow(savedStateHandle.toRoute<IncomeList>().timeRange)

        // Public so the screen can bind the search field's displayed value directly to this,
        // instead of to Content.query (which only updates after a round trip through the
        // repository's live Firestore listener). Binding to the round-tripped value is too slow
        // for a controlled text field and drops/reorders characters typed faster than the
        // round trip completes.
        val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
        private val sortOption = savedStateHandle.getStateFlow(KEY_SORT_OPTION, IncomeSortOption.DATE_DESC)
        private val retryTrigger = MutableStateFlow(0)

        private val undoEvents = Channel<Income>(Channel.BUFFERED)
        val undoEvent: Flow<Income> = undoEvents.receiveAsFlow()

        val uiState: StateFlow<IncomeListUiState> =
            combine(
                timeRange,
                query,
                sortOption,
                retryTrigger,
            ) { range, q, sort, _ ->
                Triple(range, q, sort)
            }.flatMapLatest { (range, q, sort) ->
                val instantRange = range.toInstantRange(now = Instant.now())
                incomeRepository
                    .observeIncome(instantRange.startInclusive, instantRange.endExclusive)
                    .map<List<Income>, IncomeListUiState> { income -> income.toContent(range, q, sort) }
                    .catch { e ->
                        val fallback = context?.getString(R.string.error_generic_fallback) ?: "Something went wrong"
                        emit(IncomeListUiState.Error(e.message ?: fallback))
                    }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = IncomeListUiState.Loading,
            )

        fun onTimeRangeSelected(range: TimeRange) {
            timeRange.value = range
        }

        fun onQueryChanged(newQuery: String) {
            savedStateHandle[KEY_QUERY] = newQuery
        }

        fun onSortSelected(sort: IncomeSortOption) {
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

        private fun List<Income>.toContent(
            range: TimeRange,
            query: String,
            sort: IncomeSortOption,
        ): IncomeListUiState.Content {
            val filtered =
                if (query.isBlank()) {
                    this
                } else {
                    filter { it.source.contains(query, ignoreCase = true) }
                }
            val sorted =
                when (sort) {
                    IncomeSortOption.DATE_DESC -> filtered.sortedByDescending { it.date }
                    IncomeSortOption.DATE_ASC -> filtered.sortedBy { it.date }
                    IncomeSortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.amountCents }
                    IncomeSortOption.AMOUNT_ASC -> filtered.sortedBy { it.amountCents }
                }
            return IncomeListUiState.Content(
                timeRange = range,
                query = query,
                sortOption = sort,
                income = sorted,
            )
        }

        private companion object {
            private const val KEY_QUERY = "query"
            private const val KEY_SORT_OPTION = "sortOption"
        }
    }
