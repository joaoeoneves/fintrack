package com.joaoeoneves.fintrack.ui.dashboard.recurring

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.RecurringCandidate
import com.joaoeoneves.fintrack.domain.model.recurringCandidates
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.repository.SuggestionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

private const val TAG = "RecurringSuggestionVM"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Months of expenses looked at: the current one and the three before it. */
private const val HISTORY_MONTHS = 3L

/** Home's "Make it recurring?" card: hidden, or visible with the first [candidate]. */
sealed interface RecurringSuggestionUiState {
    data object Hidden : RecurringSuggestionUiState

    data class Visible(
        val candidate: RecurringCandidate,
    ) : RecurringSuggestionUiState
}

/** Suggests turning an expense that showed up in each of the last 3 months into a recurring item. */
@HiltViewModel
class RecurringSuggestionViewModel
    @Inject
    constructor(
        expenseRepository: ExpenseRepository,
        recurringRepository: RecurringRepository,
        private val suggestionRepository: SuggestionRepository,
        private val clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<RecurringSuggestionUiState> =
            run {
                val zone = clock.zone
                val today = LocalDate.now(clock)
                val from =
                    YearMonth
                        .from(today)
                        .minusMonths(HISTORY_MONTHS)
                        .atDay(1)
                        .atStartOfDay(zone)
                        .toInstant()
                val to = today.plusDays(1).atStartOfDay(zone).toInstant()
                combine(
                    expenseRepository.observeExpenses(from, to),
                    recurringRepository.observeRecurring(),
                    suggestionRepository.observeDismissedRecurring(),
                ) { expenses, recurring, dismissed ->
                    recurringCandidates(expenses, recurring, dismissed, today, zone)
                        .firstOrNull()
                        ?.let { RecurringSuggestionUiState.Visible(it) }
                        ?: RecurringSuggestionUiState.Hidden
                }
            }.catch { e ->
                Log.w(TAG, "Recurring suggestion flow failed", e)
                emit(RecurringSuggestionUiState.Hidden)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = RecurringSuggestionUiState.Hidden,
            )

        private val dismissals = Channel<RecurringCandidate>(Channel.BUFFERED)

        /** Fires after each dismissal, so the screen can offer to undo it. */
        val dismissedEvent: Flow<RecurringCandidate> = dismissals.receiveAsFlow()

        /** Never suggests [candidate] again (until undone). */
        fun onDismiss(candidate: RecurringCandidate) {
            viewModelScope.launch {
                suggestionRepository
                    .dismissRecurring(candidate.key)
                    .onSuccess { dismissals.send(candidate) }
                    .onFailure { Log.w(TAG, "Failed to dismiss suggestion", it) }
            }
        }

        /** Takes a dismissal back. */
        fun onUndoDismiss(candidate: RecurringCandidate) {
            viewModelScope.launch {
                suggestionRepository.undismissRecurring(candidate.key).onFailure { Log.w(TAG, "Failed to undo", it) }
            }
        }
    }
