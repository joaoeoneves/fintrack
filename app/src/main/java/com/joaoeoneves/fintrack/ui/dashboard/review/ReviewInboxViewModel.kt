package com.joaoeoneves.fintrack.ui.dashboard.review

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.domain.model.reviewInbox
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.usecase.DeleteTransaction
import com.joaoeoneves.fintrack.domain.usecase.DeletedTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ReviewInboxViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** How many records the "To review" card lists; the rest is summarised as "and N more". */
const val REVIEW_INBOX_LIMIT = 5

/** Home's "To review" card: hidden, or visible with the first [items] of [total] records. */
sealed interface ReviewInboxUiState {
    data object Hidden : ReviewInboxUiState

    data class Visible(
        val items: List<Transaction>,
        val total: Int,
    ) : ReviewInboxUiState
}

/** Something the screen reports in a snackbar. */
sealed interface ReviewInboxEvent {
    /** [count] records were confirmed. */
    data class Confirmed(
        val count: Int,
    ) : ReviewInboxEvent

    /** A record was removed; the screen offers an undo. */
    data class Removed(
        val deleted: DeletedTransaction,
    ) : ReviewInboxEvent
}

/** Drives the "To review" card: the records recorded automatically and not yet confirmed or edited. */
@HiltViewModel
class ReviewInboxViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val incomeRepository: IncomeRepository,
        private val deleteTransaction: DeleteTransaction,
    ) : ViewModel() {
        private val inbox: StateFlow<List<Transaction>> =
            combine(
                expenseRepository.observeNeedingReview(),
                incomeRepository.observeNeedingReview(),
                ::reviewInbox,
            ).catch { e ->
                Log.w(TAG, "Review inbox flow failed", e)
                emit(emptyList())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

        val uiState: StateFlow<ReviewInboxUiState> =
            inbox
                .map { it.toUiState() }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    ReviewInboxUiState.Hidden,
                )

        private val events = Channel<ReviewInboxEvent>(Channel.BUFFERED)
        val eventFlow: Flow<ReviewInboxEvent> = events.receiveAsFlow()

        /** Marks every flagged record (not just the listed ones) as reviewed. */
        fun onConfirmAll() {
            val all = inbox.value
            if (all.isEmpty()) return
            viewModelScope.launch {
                val expenses = all.filterIsInstance<Expense>().map { it.id }
                val incomes = all.filterIsInstance<Income>().map { it.id }
                val results =
                    listOf(
                        if (expenses.isEmpty()) Result.success(Unit) else expenseRepository.markReviewed(expenses),
                        if (incomes.isEmpty()) Result.success(Unit) else incomeRepository.markReviewed(incomes),
                    )
                val failure = results.firstOrNull { it.isFailure }
                failure?.let { Log.w(TAG, "Failed to confirm records", it.exceptionOrNull()) }
                if (results.all { it.isSuccess }) events.send(ReviewInboxEvent.Confirmed(all.size))
            }
        }

        /** Deletes [transaction] through the shared use case and offers an undo (which keeps the review flag). */
        fun onRemove(transaction: Transaction) {
            viewModelScope.launch {
                deleteTransaction(transaction)
                    .onSuccess { events.send(ReviewInboxEvent.Removed(it)) }
                    .onFailure { Log.w(TAG, "Failed to remove record", it) }
            }
        }

        /** Takes a removal back. */
        fun onUndoRemove(deleted: DeletedTransaction) {
            viewModelScope.launch { deleteTransaction.undo(deleted) }
        }
    }

private fun List<Transaction>.toUiState(): ReviewInboxUiState =
    if (isEmpty()) ReviewInboxUiState.Hidden else ReviewInboxUiState.Visible(take(REVIEW_INBOX_LIMIT), size)
