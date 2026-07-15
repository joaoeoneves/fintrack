package com.joaoeoneves.fintrack.ui.expense.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.TimeRange
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.ui.navigation.ExpenseList
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
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseListViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val timeRange = MutableStateFlow(savedStateHandle.toRoute<ExpenseList>().timeRange)
        private val query = MutableStateFlow("")
        private val sortOption = MutableStateFlow(SortOption.DATE_DESC)
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
                    .catch { e -> emit(ExpenseListUiState.Error(e.message ?: "Something went wrong")) }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ExpenseListUiState.Loading,
            )

        fun onTimeRangeSelected(range: TimeRange) {
            timeRange.value = range
        }

        fun onQueryChanged(newQuery: String) {
            query.value = newQuery
        }

        fun onSortSelected(sort: SortOption) {
            sortOption.value = sort
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
    }
