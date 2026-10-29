package com.joaoeoneves.fintrack.ui.recurring

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.ExpectedIncome
import com.joaoeoneves.fintrack.domain.model.IncomeRow
import com.joaoeoneves.fintrack.domain.model.UpcomingOccurrence
import com.joaoeoneves.fintrack.domain.model.payingLinesByPayMonth
import com.joaoeoneves.fintrack.domain.usecase.HandleRecurringOccurrence
import com.joaoeoneves.fintrack.domain.usecase.ObserveExpectedIncome
import com.joaoeoneves.fintrack.domain.usecase.ObserveUpcomingRecurring
import com.joaoeoneves.fintrack.domain.usecase.ReceiveRequest
import com.joaoeoneves.fintrack.domain.usecase.RecordPayday
import com.joaoeoneves.fintrack.domain.usecase.RecurringOverview
import com.joaoeoneves.fintrack.domain.usecase.UndoRecurringOccurrence
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.recurring.RecurringUndo.Kind
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

/** What a confirm or skip of a recurring occurrence did, kept so the snackbar can take it back. */
data class RecurringUndo(
    val kind: Kind,
    val itemId: String,
    val itemName: String,
    val date: LocalDate,
    val previousHandledThrough: LocalDate?,
) {
    enum class Kind { CONFIRMED, SKIPPED }
}

/** Drives the dashboard's upcoming-recurring card: what is due now (Confirm / Skip) and the income to mark received. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UpcomingRecurringViewModel
    @Inject
    constructor(
        observeUpcomingRecurring: ObserveUpcomingRecurring,
        observeExpectedIncome: ObserveExpectedIncome,
        private val handleRecurringOccurrence: HandleRecurringOccurrence,
        private val recordPayday: RecordPayday,
        private val undoRecurringOccurrence: UndoRecurringOccurrence,
        private val clock: Clock,
    ) : ViewModel() {
        private val retryTrigger = MutableStateFlow(0)
        private val errors = Channel<UiText>(Channel.BUFFERED)
        private val undos = Channel<RecurringUndo>(Channel.BUFFERED)

        /** One-shot user-facing messages for confirm/skip writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        /** Each confirm or skip just done, for a snackbar with "Undo" (see [onUndo]). */
        val undoEvent: Flow<RecurringUndo> = undos.receiveAsFlow()

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

        /** Confirms [row], with [amountCents] instead of the item's amount when set (a bill that varies). */
        fun onConfirm(
            row: UpcomingOccurrence,
            amountCents: Long? = null,
        ) {
            viewModelScope.launch {
                handleRecurringOccurrence
                    .confirm(row.item.id, row.date, LocalDate.now(clock), clock.zone, amountCents)
                    .onSuccess { undos.send(row.undo(RecurringUndo.Kind.CONFIRMED)) }
                    .onFailure { reportFailure(it) }
            }
        }

        /** Takes back the confirm or skip described by [undo]. */
        fun onUndo(undo: RecurringUndo) {
            viewModelScope.launch {
                val result =
                    when (undo.kind) {
                        RecurringUndo.Kind.CONFIRMED ->
                            undoRecurringOccurrence.undoConfirm(undo.itemId, undo.date, undo.previousHandledThrough)
                        RecurringUndo.Kind.SKIPPED ->
                            undoRecurringOccurrence.undoSkip(undo.itemId, undo.date, undo.previousHandledThrough)
                    }
                result.onFailure { reportFailure(it) }
            }
        }

        /** Records [row] of the current month's expected income as received on [date]. */
        fun onReceiveIncome(
            row: IncomeRow,
            date: LocalDate,
            amountCents: Long?,
        ): () -> Unit {
            val content = uiState.value as? UpcomingRecurringUiState.Content
            val expected = content?.expectedIncome
            if (content == null || expected == null) return {}
            // The row is paid this month or last: it is recorded under the month it is paid in.
            val payMonth = expected.entries.firstOrNull { it.row == row }?.month ?: expected.month
            val byPayMonth = row.payingLinesByPayMonth(payMonth, expected.incomeMonth)
            viewModelScope.launch {
                for ((month, lines) in byPayMonth) {
                    val request =
                        ReceiveRequest(
                            lines = lines,
                            month = month,
                            workingDays = expected.incomeMonth.workingDays(month),
                            date = date,
                            today = content.today,
                            zone = clock.zone,
                            amountOverrideCents = amountCents,
                        )
                    val failure = recordPayday.receive(request).exceptionOrNull()
                    if (failure != null) {
                        Log.w(TAG, "Failed to record the received income", failure)
                        errors.send(UiText.StringResource(R.string.error_plan_save_failed))
                        break
                    }
                }
            }
            return {
                viewModelScope.launch {
                    for ((month, lines) in byPayMonth) {
                        val failure = recordPayday.unreceive(lines, month).exceptionOrNull()
                        if (failure != null) {
                            Log.w(TAG, "Failed to undo the received income", failure)
                            errors.send(UiText.StringResource(R.string.error_plan_save_failed))
                            break
                        }
                    }
                }
                Unit
            }
        }

        fun onSkip(row: UpcomingOccurrence) {
            viewModelScope.launch {
                handleRecurringOccurrence
                    .skip(row.item.id, row.date)
                    .onSuccess { undos.send(row.undo(RecurringUndo.Kind.SKIPPED)) }
                    .onFailure { reportFailure(it) }
            }
        }

        private suspend fun reportFailure(error: Throwable) {
            Log.w(TAG, "Failed to handle a recurring occurrence", error)
            errors.send(UiText.StringResource(R.string.error_recurring_save_failed))
        }
    }

private fun UpcomingOccurrence.undo(kind: Kind) = RecurringUndo(kind, item.id, item.name, date, item.handledThrough)
