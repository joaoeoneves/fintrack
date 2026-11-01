package com.joaoeoneves.fintrack.ui.dashboard.awaiting

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.OwedRefund
import com.joaoeoneves.fintrack.domain.model.owedRefunds
import com.joaoeoneves.fintrack.domain.model.totalRemainingCents
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val TAG = "AwaitingRefundViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** How many expenses the "Awaiting refund" card lists; the rest is summarised as "and N more". */
const val AWAITING_REFUND_LIMIT = 5

/** Home's "Awaiting refund" card: hidden, or the first [items] of [total] owed expenses and what is owed in all. */
sealed interface AwaitingRefundUiState {
    data object Hidden : AwaitingRefundUiState

    data class Visible(
        val items: List<OwedRefund>,
        val total: Int,
        val totalRemainingCents: Long,
    ) : AwaitingRefundUiState
}

/** The "Awaiting refund" card's state plus its callbacks. */
data class AwaitingRefundModel(
    val state: AwaitingRefundUiState = AwaitingRefundUiState.Hidden,
    val onOpenExpense: (String) -> Unit = {},
    /** Opens the refund form for the given owed expense, prefilled; nothing is saved until it is confirmed. */
    val onRecordRefund: (OwedRefund) -> Unit = {},
)

/** Whether [AwaitingRefundCard] has anything to show. */
val AwaitingRefundModel.hasContent: Boolean get() = state is AwaitingRefundUiState.Visible

/** Drives the "Awaiting refund" card: expenses marked "Will be refunded" that are not fully refunded yet. */
@HiltViewModel
class AwaitingRefundViewModel
    @Inject
    constructor(
        expenseRepository: ExpenseRepository,
    ) : ViewModel() {
        val uiState: StateFlow<AwaitingRefundUiState> =
            combine(
                expenseRepository.observeAwaitingRefund(),
                expenseRepository.observeLinkedRefunds(),
            ) { awaiting, refunds -> owedRefunds(awaiting, refunds).toUiState() }
                .catch { e ->
                    Log.w(TAG, "Awaiting refund flow failed", e)
                    emit(AwaitingRefundUiState.Hidden)
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    AwaitingRefundUiState.Hidden,
                )
    }

internal fun List<OwedRefund>.toUiState(): AwaitingRefundUiState =
    if (isEmpty()) {
        AwaitingRefundUiState.Hidden
    } else {
        AwaitingRefundUiState.Visible(take(AWAITING_REFUND_LIMIT), size, totalRemainingCents())
    }
