package ptech.joaoe.agenticusage.ui.expense.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.TimeRange
import ptech.joaoe.agenticusage.domain.repository.ExpenseRepository
import ptech.joaoe.agenticusage.ui.navigation.ExpenseList

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val timeRange = MutableStateFlow(savedStateHandle.toRoute<ExpenseList>().timeRange)
    private val query = MutableStateFlow("")
    private val sortOption = MutableStateFlow(SortOption.DATE_DESC)

    val uiState: StateFlow<ExpenseListUiState> = combine(timeRange, query, sortOption) { range, q, sort ->
        Triple(range, q, sort)
    }
        .flatMapLatest { (range, q, sort) ->
            val instantRange = range.toInstantRange(now = Instant.now())
            expenseRepository.observeExpenses(instantRange.startInclusive, instantRange.endExclusive)
                .map { expenses -> expenses.toContent(range, q, sort) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExpenseListUiState.Loading
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

    fun onDeleteExpense(id: String) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(id)
        }
    }

    private fun List<Expense>.toContent(range: TimeRange, query: String, sort: SortOption): ExpenseListUiState.Content {
        val filtered = if (query.isBlank()) {
            this
        } else {
            filter { it.name.contains(query, ignoreCase = true) }
        }
        val sorted = when (sort) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.date }
            SortOption.DATE_ASC -> filtered.sortedBy { it.date }
            SortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.amountCents }
            SortOption.AMOUNT_ASC -> filtered.sortedBy { it.amountCents }
        }
        return ExpenseListUiState.Content(
            timeRange = range,
            query = query,
            sortOption = sort,
            expenses = sorted
        )
    }
}
