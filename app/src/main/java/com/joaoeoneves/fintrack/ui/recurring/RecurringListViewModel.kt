package com.joaoeoneves.fintrack.ui.recurring

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.billsOf
import com.joaoeoneves.fintrack.domain.model.firstPendingOccurrence
import com.joaoeoneves.fintrack.domain.model.isEnded
import com.joaoeoneves.fintrack.domain.model.recurringCostOf
import com.joaoeoneves.fintrack.domain.model.resolve
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
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
        private val clock: Clock,
    ) : ViewModel() {
        private val retryTrigger = MutableStateFlow(0)
        private val errors = Channel<UiText>(Channel.BUFFERED)
        private val deletions = Channel<RecurringItem>(Channel.BUFFERED)

        /** Each item just deleted, for an undo snackbar. */
        val deletedEvent: Flow<RecurringItem> = deletions.receiveAsFlow()

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
                    ) { items, catalog ->
                        // Recurring income lives in the income lines; savings items are listed as investments.
                        contentOf(billsOf(items), catalog, today)
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

        /** Deletes the item at once; the screen offers to undo (its past transactions are never touched). */
        fun onDeleteRequested(id: String) {
            viewModelScope.launch {
                val item = latest(id) ?: return@launch
                recurringRepository
                    .deleteRecurring(id)
                    .onSuccess { deletions.send(item) }
                    .onFailure { reportFailure(it) }
            }
        }

        /** Puts back an item deleted a moment ago. */
        fun onUndoDelete(item: RecurringItem) {
            viewModelScope.launch { recurringRepository.saveRecurring(item).onFailure { reportFailure(it) } }
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
    val (investments, bills) = rows.partition { it.category?.kind == CategoryKind.SAVING }
    val (active, inactive) = bills.partition { it.item.active && !it.isEnded }
    return RecurringListUiState.Content(
        active = active,
        inactive = inactive,
        investments = investments,
        cost = recurringCostOf(items, today),
    )
}
