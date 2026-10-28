package com.joaoeoneves.fintrack.ui.plan.envelopes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.settings.categories.MoveDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "EnvelopesViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** What a row of the envelopes list says about the envelope's budget. */
sealed interface EnvelopeRowStatus {
    /** No categories and no amount yet. */
    data object Empty : EnvelopeRowStatus

    /** Categories without any limit: the envelope only records spending. */
    data object Tracking : EnvelopeRowStatus

    /** A monthly budget of [cents], fixed or the sum of the category limits. */
    data class Budgeted(
        val cents: Long,
    ) : EnvelopeRowStatus
}

data class EnvelopeRowUi(
    val id: String,
    val name: String,
    val categoryCount: Int,
    val status: EnvelopeRowStatus,
)

sealed interface EnvelopesUiState {
    data object Loading : EnvelopesUiState

    data class Content(
        val rows: List<EnvelopeRowUi>,
    ) : EnvelopesUiState

    data class Error(
        val message: UiText,
    ) : EnvelopesUiState
}

/** The envelopes list: one row per envelope with its category count and budget, reorderable. */
@HiltViewModel
class EnvelopesViewModel
    @Inject
    constructor(
        private val envelopeRepository: EnvelopeRepository,
        categoryRepository: CategoryRepository,
        budgetRepository: BudgetRepository,
    ) : ViewModel() {
        private val errors = Channel<UiText>(Channel.BUFFERED)

        // Latest envelope order seen by uiState, read by onMove.
        @Volatile
        private var currentIds: List<String> = emptyList()

        /** One-shot messages for writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        val uiState: StateFlow<EnvelopesUiState> =
            combine(
                envelopeRepository.observeEnvelopes().onEach { list -> currentIds = list.map { it.id } },
                categoryRepository.observeCategories(),
                budgetRepository.observeBudgets(),
            ) { envelopes, categories, budgets ->
                EnvelopesUiState.Content(rowsOf(envelopes, categories, budgets)) as EnvelopesUiState
            }.catch {
                Log.w(TAG, "Failed to observe the envelopes", it)
                emit(EnvelopesUiState.Error(UiText.StringResource(R.string.error_group_save_failed)))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), EnvelopesUiState.Loading)

        /** Moves the envelope [id] one place up or down. */
        fun onMove(
            id: String,
            direction: MoveDirection,
        ) {
            val ids = currentIds
            val index = ids.indexOf(id)
            val target = if (direction == MoveDirection.UP) index - 1 else index + 1
            if (index < 0 || target !in ids.indices) return
            val reordered = ids.toMutableList().apply { add(target, removeAt(index)) }
            viewModelScope.launch {
                envelopeRepository.reorderEnvelopes(reordered).onFailure {
                    Log.w(TAG, "Failed to reorder the envelopes", it)
                    errors.send(UiText.StringResource(R.string.error_group_save_failed))
                }
            }
        }
    }

internal fun rowsOf(
    envelopes: List<Envelope>,
    categories: List<Category>,
    budgets: List<Budget>,
): List<EnvelopeRowUi> {
    val limits = budgets.associate { it.categoryId to it.limitCents }
    return envelopes.map { envelope ->
        val members = categories.filter { it.groupId == envelope.id }
        val limitSum = members.sumOf { limits[it.id] ?: 0L }
        val amount = envelope.amountCents
        val status =
            when {
                amount != null -> EnvelopeRowStatus.Budgeted(amount)
                limitSum > 0L -> EnvelopeRowStatus.Budgeted(limitSum)
                members.isEmpty() -> EnvelopeRowStatus.Empty
                else -> EnvelopeRowStatus.Tracking
            }
        EnvelopeRowUi(envelope.id, envelope.name, members.size, status)
    }
}
