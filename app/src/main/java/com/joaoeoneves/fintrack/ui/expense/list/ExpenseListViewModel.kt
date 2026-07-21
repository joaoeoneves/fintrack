package com.joaoeoneves.fintrack.ui.expense.list

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.ui.navigation.ExpenseList
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseListViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val savedStateHandle: SavedStateHandle,
        // Nullable with a default so existing unit tests that construct this ViewModel directly
        // (bypassing Hilt) keep compiling; Hilt itself always supplies a real ApplicationContext in
        // production. When null (test-only), the fallback below matches the exact literal those
        // tests assert on.
        @param:ApplicationContext private val context: Context? = null,
    ) : ViewModel() {
        private val timeRange = MutableStateFlow(savedStateHandle.toRoute<ExpenseList>().timeRange)

        // Public so the screen can bind the search field's displayed value directly to this,
        // instead of to Content.query (which only updates after a round trip through the
        // repository's live Firestore listener). Binding to the round-tripped value is too slow
        // for a controlled text field and drops/reorders characters typed faster than the
        // round trip completes.
        val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
        private val sortOption = savedStateHandle.getStateFlow(KEY_SORT_OPTION, SortOption.DATE_DESC)
        private val retryTrigger = MutableStateFlow(0)

        private val undoEvents = Channel<Expense>(Channel.BUFFERED)
        val undoEvent: Flow<Expense> = undoEvents.receiveAsFlow()

        val uiState: StateFlow<ExpenseListUiState> =
            combine(
                timeRange,
                query,
                sortOption,
                retryTrigger,
            ) { range, q, sort, _ ->
                Triple(range, q, sort)
            }.flatMapLatest { (range, q, sort) ->
                val instantRange = range.toInstantRange(now = Instant.now())
                expenseRepository
                    .observeExpenses(instantRange.startInclusive, instantRange.endExclusive)
                    .map<List<Expense>, ExpenseListUiState> { expenses -> expenses.toContent(range, q, sort) }
                    .catch { e ->
                        val fallback = context?.getString(R.string.error_generic_fallback) ?: "Something went wrong"
                        emit(ExpenseListUiState.Error(e.message ?: fallback))
                    }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ExpenseListUiState.Loading,
            )

        fun onTimeRangeSelected(range: TimeRange) {
            timeRange.value = range
        }

        fun onQueryChanged(newQuery: String) {
            savedStateHandle[KEY_QUERY] = newQuery
        }

        fun onSortSelected(sort: SortOption) {
            savedStateHandle[KEY_SORT_OPTION] = sort
        }

        fun onRetry() {
            retryTrigger.value++
        }

        fun onDeleteExpense(id: String) {
            viewModelScope.launch {
                val currentState = uiState.value
                val expenseToDelete =
                    (currentState as? ExpenseListUiState.Content)
                        ?.expenses
                        ?.find { it.id == id }

                val result = expenseRepository.deleteExpense(id)

                if (result.isSuccess && expenseToDelete != null) {
                    undoEvents.send(expenseToDelete)
                }
            }
        }

        fun onUndoDelete(expense: Expense) {
            viewModelScope.launch {
                expenseRepository.addExpense(expense)
            }
        }

        private fun List<Expense>.toContent(
            range: TimeRange,
            query: String,
            sort: SortOption,
        ): ExpenseListUiState.Content {
            val filtered =
                if (query.isBlank()) {
                    this
                } else {
                    filter { it.name.contains(query, ignoreCase = true) }
                }
            val sorted =
                when (sort) {
                    SortOption.DATE_DESC -> filtered.sortedByDescending { it.date }
                    SortOption.DATE_ASC -> filtered.sortedBy { it.date }
                    SortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.amountCents }
                    SortOption.AMOUNT_ASC -> filtered.sortedBy { it.amountCents }
                }
            return ExpenseListUiState.Content(
                timeRange = range,
                query = query,
                sortOption = sort,
                expenses = sorted,
            )
        }

        private companion object {
            private const val KEY_QUERY = "query"
            private const val KEY_SORT_OPTION = "sortOption"
        }
    }
