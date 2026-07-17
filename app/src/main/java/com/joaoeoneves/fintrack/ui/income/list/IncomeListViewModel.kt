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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncomeListViewModel
    @Inject
    constructor(
        private val incomeRepository: IncomeRepository,
        savedStateHandle: SavedStateHandle,
        // Nullable with a default so existing unit tests that construct this ViewModel directly
        // (bypassing Hilt) keep compiling; Hilt itself always supplies a real ApplicationContext in
        // production. When null (test-only), the fallback below matches the exact literal those
        // tests assert on.
        @param:ApplicationContext private val context: Context? = null,
    ) : ViewModel() {
        private val timeRange = MutableStateFlow(savedStateHandle.toRoute<IncomeList>().timeRange)
        private val retryTrigger = MutableStateFlow(0)

        private val undoEvents = Channel<Income>(Channel.BUFFERED)
        val undoEvent: Flow<Income> = undoEvents.receiveAsFlow()

        val uiState: StateFlow<IncomeListUiState> =
            combine(timeRange, retryTrigger) { range, _ -> range }
                .flatMapLatest { range ->
                    val instantRange = range.toInstantRange(now = Instant.now())
                    incomeRepository
                        .observeIncome(instantRange.startInclusive, instantRange.endExclusive)
                        .map<List<Income>, IncomeListUiState> { income -> income.toContent(range) }
                        .catch { e ->
                            val fallback =
                                context?.getString(R.string.error_generic_fallback) ?: "Something went wrong"
                            emit(IncomeListUiState.Error(e.message ?: fallback))
                        }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = IncomeListUiState.Loading,
                )

        fun onTimeRangeSelected(range: TimeRange) {
            timeRange.value = range
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

        private fun List<Income>.toContent(range: TimeRange): IncomeListUiState.Content =
            IncomeListUiState.Content(
                timeRange = range,
                income = sortedByDescending { it.date },
            )
    }
