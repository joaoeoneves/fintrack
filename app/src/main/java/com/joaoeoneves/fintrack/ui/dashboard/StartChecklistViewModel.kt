package com.joaoeoneves.fintrack.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.data.preferences.StartChecklistState
import com.joaoeoneves.fintrack.data.preferences.StartChecklistStore
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.di.StartChecklistSettleMillis
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.StartChecklist
import com.joaoeoneves.fintrack.domain.model.StartStep
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import com.joaoeoneves.fintrack.domain.usecase.ObserveStartChecklist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "StartChecklistVM"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** At least this many steps done on first sight means the user is already set up: the card is retired. */
private const val RETIRE_THRESHOLD = 4

/** One checklist row: [step] and whether it is [done]. */
data class StartStepUi(
    val step: StartStep,
    val done: Boolean,
)

/** Home's "Get started" card: hidden, or visible with its [steps] and progress. */
sealed interface StartChecklistUiState {
    data object Hidden : StartChecklistUiState

    data class Visible(
        val steps: List<StartStepUi>,
        val doneCount: Int,
        val total: Int,
    ) : StartChecklistUiState
}

/**
 * Drives the "Get started" card. The first time it is seen (once a session exists and the data has settled) the
 * decision is stored: an already set-up user retires the card, anyone else sees it until every step is done or
 * it is dismissed.
 */
@HiltViewModel
class StartChecklistViewModel
    @Inject
    constructor(
        private val store: StartChecklistStore,
        private val observeChecklist: ObserveStartChecklist,
        private val sessionRepository: SessionRepository,
        @param:StartChecklistSettleMillis private val settleMillis: Long,
    ) : ViewModel() {
        val uiState: StateFlow<StartChecklistUiState> =
            combine(store.state, observeChecklist()) { state, checklist ->
                if (state == StartChecklistState.ACTIVE && !checklist.isComplete) {
                    checklist.toVisible()
                } else {
                    StartChecklistUiState.Hidden
                }
            }.catch { e ->
                Log.w(TAG, "Start checklist flow failed", e)
                emit(StartChecklistUiState.Hidden)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = StartChecklistUiState.Hidden,
            )

        init {
            viewModelScope.launch {
                store.state
                    .map { it == null }
                    .distinctUntilChanged()
                    .collectLatest { undecided -> if (undecided) decide() }
            }
        }

        fun onDismiss() {
            viewModelScope.launch { store.set(StartChecklistState.DISMISSED) }
        }

        private suspend fun decide() {
            runCatchingCancellable {
                sessionRepository.session.first { it is Session.Guest || it is Session.SignedIn }
                delay(settleMillis)
                val checklist = observeChecklist().first()
                val alreadySetUp = checklist.doneCount >= RETIRE_THRESHOLD
                store.set(if (alreadySetUp) StartChecklistState.RETIRED else StartChecklistState.ACTIVE)
            }.onFailure { e ->
                Log.w(TAG, "Could not decide whether to show the start checklist", e)
            }
        }

        private fun StartChecklist.toVisible() =
            StartChecklistUiState.Visible(
                steps = StartStep.entries.map { StartStepUi(it, it in done) },
                doneCount = doneCount,
                total = total,
            )
    }
