package ptech.joaoe.agenticusage.ui.dashboard

import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ptech.joaoe.agenticusage.domain.model.Expense
import ptech.joaoe.agenticusage.domain.model.ExpenseCategory
import ptech.joaoe.agenticusage.domain.model.TimeRange
import ptech.joaoe.agenticusage.domain.repository.ExpenseRepository

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val timeRange = MutableStateFlow(TimeRange.ONE_MONTH)
    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = combine(timeRange, retryTrigger) { range, _ -> range }
        .flatMapLatest { range ->
            val instantRange = range.toInstantRange(now = Instant.now())
            expenseRepository.observeExpenses(instantRange.startInclusive, instantRange.endExclusive)
                .map<List<Expense>, DashboardUiState> { expenses -> expenses.toContent(range) }
                .catch { e -> emit(DashboardUiState.Error(e.message ?: "Something went wrong")) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState.Loading
        )

    fun onTimeRangeSelected(range: TimeRange) {
        timeRange.value = range
    }

    fun onRetry() {
        retryTrigger.value++
    }

    private fun List<Expense>.toContent(range: TimeRange): DashboardUiState.Content {
        val categoryTotals = ExpenseCategory.entries.map { category ->
            CategoryTotal(
                category = category,
                totalCents = filter { it.category == category }.sumOf { it.amountCents }
            )
        }
        return DashboardUiState.Content(
            timeRange = range,
            expenses = this,
            categoryTotals = categoryTotals,
            totalCents = sumOf { it.amountCents },
            recentExpenses = sortedByDescending { it.date }.take(5)
        )
    }
}
