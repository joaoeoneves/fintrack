package com.joaoeoneves.fintrack.ui.recurring

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.ExpectedIncome
import com.joaoeoneves.fintrack.domain.usecase.ObserveExpectedIncome
import com.joaoeoneves.fintrack.domain.usecase.ObserveUpcomingRecurring
import com.joaoeoneves.fintrack.domain.usecase.RecurringOverview
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        /** The month's income still to be marked received; `null` when it could not be observed. */
        val expectedIncome: ExpectedIncome? = null,
    ) : UpcomingRecurringUiState

    data class Error(
        val message: UiText,
    ) : UpcomingRecurringUiState
}

/** Drives the dashboard's read-only upcoming-recurring card: the bills coming up and the income still expected. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UpcomingRecurringViewModel
    @Inject
    constructor(
        observeUpcomingRecurring: ObserveUpcomingRecurring,
        observeExpectedIncome: ObserveExpectedIncome,
        private val clock: Clock,
    ) : ViewModel() {
        private val retryTrigger = MutableStateFlow(0)
        val uiState: StateFlow<UpcomingRecurringUiState> =
            retryTrigger
                .flatMapLatest {
                    // "Today" is read on every (re)subscription so a resume after midnight recomputes it.
                    val today = LocalDate.now(clock)
                    // The income group is optional: a failure there leaves the upcoming list intact.
                    val expectedIncome =
                        observeExpectedIncome(today, clock.zone)
                            .map<ExpectedIncome, ExpectedIncome?> { it }
                            .catch {
                                Log.w(TAG, "Failed to observe the expected income", it)
                                emit(null)
                            }
                    combine(observeUpcomingRecurring(today), expectedIncome) { overview, income ->
                        UpcomingRecurringUiState.Content(overview, today, income) as UpcomingRecurringUiState
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
    }
