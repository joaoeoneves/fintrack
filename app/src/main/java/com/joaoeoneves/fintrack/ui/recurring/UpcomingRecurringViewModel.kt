package com.joaoeoneves.fintrack.ui.recurring

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.UpcomingOccurrence
import com.joaoeoneves.fintrack.domain.usecase.HandleRecurringOccurrence
import com.joaoeoneves.fintrack.domain.usecase.ObserveUpcomingRecurring
import com.joaoeoneves.fintrack.domain.usecase.RecurringOverview
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "UpcomingRecurringVM"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** State of the dashboard's upcoming-recurring card; [Content.today] is the day it was computed for. */
sealed interface UpcomingRecurringUiState {
    data object Loading : UpcomingRecurringUiState

    data class Content(
        val overview: RecurringOverview,
        val today: LocalDate,
    ) : UpcomingRecurringUiState

    data class Error(
        val message: UiText,
    ) : UpcomingRecurringUiState
}

/** Drives the dashboard's upcoming-recurring card: what is due now and Confirm / Skip. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UpcomingRecurringViewModel
    @Inject
    constructor(
        observeUpcomingRecurring: ObserveUpcomingRecurring,
        private val handleRecurringOccurrence: HandleRecurringOccurrence,
        private val clock: Clock,
    ) : ViewModel() {
        private val retryTrigger = MutableStateFlow(0)
        private val errors = Channel<UiText>(Channel.BUFFERED)
        private val messages = Channel<String>(Channel.BUFFERED)

        /** One-shot user-facing messages for confirm/skip writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        /** The name of each item whose occurrence was just confirmed, for an "Added ..." snackbar. */
        val messageEvent: Flow<String> = messages.receiveAsFlow()

        val uiState: StateFlow<UpcomingRecurringUiState> =
            retryTrigger
                .flatMapLatest {
                    // "Today" is read on every (re)subscription so a resume after midnight recomputes it.
                    val today = LocalDate.now(clock)
                    observeUpcomingRecurring(today)
                        .map<RecurringOverview, UpcomingRecurringUiState> {
                            UpcomingRecurringUiState.Content(it, today)
                        }.catch { e ->
                            Log.w(TAG, "Failed to observe upcoming recurring items", e)
                            emit(UpcomingRecurringUiState.Error(UiText.StringResource(R.string.error_generic_fallback)))
                        }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = UpcomingRecurringUiState.Loading,
                )

        fun onRetry() {
            retryTrigger.value++
        }

        fun onConfirm(row: UpcomingOccurrence) {
            viewModelScope.launch {
                handleRecurringOccurrence
                    .confirm(row.item.id, row.date, LocalDate.now(clock), clock.zone)
                    .onSuccess { messages.send(row.item.name) }
                    .onFailure { reportFailure(it) }
            }
        }

        fun onSkip(row: UpcomingOccurrence) {
            viewModelScope.launch {
                handleRecurringOccurrence.skip(row.item.id, row.date).onFailure { reportFailure(it) }
            }
        }

        private suspend fun reportFailure(error: Throwable) {
            Log.w(TAG, "Failed to handle a recurring occurrence", error)
            errors.send(UiText.StringResource(R.string.error_recurring_save_failed))
        }
    }
