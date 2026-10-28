package com.joaoeoneves.fintrack.ui.recurring

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.annualizedCents
import com.joaoeoneves.fintrack.domain.model.firstPendingOccurrence
import com.joaoeoneves.fintrack.domain.model.isEnded
import com.joaoeoneves.fintrack.domain.model.paused
import com.joaoeoneves.fintrack.domain.model.resolve
import com.joaoeoneves.fintrack.domain.model.resumed
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "RecurringListViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Lists the recurring items and handles pause/resume and delete. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecurringListViewModel
    @Inject
    constructor(
        private val recurringRepository: RecurringRepository,
        private val categoryRepository: CategoryRepository,
        private val goalRepository: GoalRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val retryTrigger = MutableStateFlow(0)
        private val deleteTarget = MutableStateFlow<RecurringItem?>(null)
        private val errors = Channel<UiText>(Channel.BUFFERED)

        /** One-shot user-facing messages for writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        val uiState: StateFlow<RecurringListUiState> =
            retryTrigger
                .flatMapLatest {
                    // "Today" is read on every (re)subscription so a resume after midnight recomputes it.
                    val today = LocalDate.now(clock)
                    combine(
                        recurringRepository.observeRecurring(),
                        categoryRepository.observeCategories(),
                        deleteTarget,
                        goalRepository.observeGoals(),
                    ) { items, catalog, target, goals ->
                        // A savings plan's monthly item lives in the plan, not in Contas.
                        val planOwned = goals.mapNotNullTo(mutableSetOf()) { it.recurringId }
                        // Recurring income lives in the income lines; a stray INCOME item is not a bill.
                        val bills = items.filter { it.id !in planOwned && it.type == RecurringType.EXPENSE }
                        contentOf(bills, catalog, target, today)
                    }.catch { e ->
                        Log.w(TAG, "Failed to observe recurring items", e)
                        emit(RecurringListUiState.Error(UiText.StringResource(R.string.error_generic_fallback)))
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = RecurringListUiState.Loading,
                )

        fun onRetry() {
            retryTrigger.value++
        }

        /**
         * Pauses an active item (see [paused]); resuming one (see [resumed]) skips the occurrences of the
         * paused period rather than offering them as overdue.
         */
        fun onTogglePaused(id: String) {
            viewModelScope.launch {
                val item = latest(id) ?: return@launch
                val today = LocalDate.now(clock)
                val updated = if (item.active) item.paused(today) else item.resumed(today)
                recurringRepository.saveRecurring(updated).onFailure { reportFailure(it) }
            }
        }

        fun onDeleteRequested(id: String) {
            viewModelScope.launch { deleteTarget.value = latest(id) }
        }

        fun onConfirmDelete() {
            val target = deleteTarget.value ?: return
            deleteTarget.value = null
            viewModelScope.launch {
                recurringRepository.deleteRecurring(target.id).onFailure { reportFailure(it) }
            }
        }

        fun onDismissDelete() {
            deleteTarget.value = null
        }

        private suspend fun latest(id: String): RecurringItem? {
            val items = recurringRepository.observeRecurring().first()
            return items.firstOrNull { it.id == id }
        }

        private suspend fun reportFailure(error: Throwable) {
            Log.w(TAG, "Failed to update a recurring item", error)
            errors.send(UiText.StringResource(R.string.error_recurring_save_failed))
        }
    }

private fun contentOf(
    items: List<RecurringItem>,
    catalog: List<Category>,
    deleteTarget: RecurringItem?,
    today: LocalDate,
): RecurringListUiState {
    val rows =
        items.map { item ->
            val ended = item.isEnded(today)
            RecurringRow(
                item = item,
                category = item.categoryId?.let { catalog.resolve(it) },
                nextDue = item.firstPendingOccurrence().takeIf { item.active && !ended },
                isEnded = ended,
            )
        }
    val (active, inactive) = rows.partition { it.item.active && !it.isEnded }
    return RecurringListUiState.Content(
        active = active,
        inactive = inactive,
        annualExpenseCents = annualizedCents(items, RecurringType.EXPENSE, today),
        deleteTarget = deleteTarget,
    )
}
