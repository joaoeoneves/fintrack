package com.joaoeoneves.fintrack.ui.plan.salary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.isManaged
import com.joaoeoneves.fintrack.domain.model.salaryBreakdownOf
import com.joaoeoneves.fintrack.domain.model.workingDaysIn
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import com.joaoeoneves.fintrack.domain.usecase.ManageSalaryProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

private const val TAG = "SalaryProfileViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Edits the salary profile and keeps the plan's managed income lines in sync with it. */
@HiltViewModel
class SalaryProfileViewModel
    @Inject
    constructor(
        private val profiles: SalaryProfileRepository,
        private val manageProfile: ManageSalaryProfile,
        private val plannedIncome: PlannedIncomeRepository,
        private val clock: Clock,
    ) : ViewModel() {
        // `null` until the stored profile has been read into the form.
        private val form = MutableStateFlow<SalaryForm?>(null)
        private val hasSavedProfile = MutableStateFlow(false)
        private val saving = MutableStateFlow(false)
        private val events = Channel<SalaryProfileEvent>(Channel.BUFFERED)

        private val duplicateCandidates = MutableStateFlow<List<PlannedIncome>>(emptyList())

        /** The user's own monthly lines (no earmark) found after a save, to offer removing; empty = no dialog. */
        val duplicates: StateFlow<List<PlannedIncome>> = duplicateCandidates

        val eventFlow: Flow<SalaryProfileEvent> = events.receiveAsFlow()

        val uiState: StateFlow<SalaryProfileUiState> =
            combine(form, hasSavedProfile, saving) { current, saved, busy ->
                current?.let { contentOf(it, saved, busy) } ?: SalaryProfileUiState.Loading
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = SalaryProfileUiState.Loading,
            )

        /** Loads the stored profile into the form once; amounts are formatted with [decimalSeparator]. */
        fun start(decimalSeparator: Char) {
            if (form.value != null) return
            viewModelScope.launch {
                val stored = profiles.observeSalaryProfile().first()
                hasSavedProfile.value = stored != null
                form.value = stored?.toForm(decimalSeparator) ?: SalaryForm()
            }
        }

        fun onFormChange(new: SalaryForm) {
            form.value = new
        }

        /** Stores the profile and regenerates the managed lines, naming new ones with [labels]. */
        fun onSave(labels: SalaryLineLabels) {
            val current = form.value ?: return
            val result = current.toResult()
            if (saving.value || !result.hasRequiredAmount || result.invalid.isNotEmpty()) return
            viewModelScope.launch {
                saving.value = true
                manageProfile
                    .save(result.profile, labels)
                    .onSuccess { finishSave() }
                    .onFailure { reportFailure(it) }
                saving.value = false
            }
        }

        // Leaves the screen, unless manual monthly lines may now be counted twice: then asks about them first.
        private suspend fun finishSave() {
            val others =
                plannedIncome
                    .observePlannedIncome()
                    .first()
                    .filter { !it.isManaged && it.frequency == IncomeFrequency.MONTHLY && it.earmark == null }
            if (others.isEmpty()) {
                events.send(SalaryProfileEvent.Done)
            } else {
                duplicateCandidates.value = others
            }
        }

        /** Deletes the lines with [ids] (the ones the user ticked) and leaves the screen. */
        fun onRemoveDuplicates(ids: Set<String>) {
            viewModelScope.launch {
                val failed = ids.map { plannedIncome.deletePlannedIncome(it) }.any { it.isFailure }
                duplicateCandidates.value = emptyList()
                events.send(if (failed) SalaryProfileEvent.Failed else SalaryProfileEvent.Done)
            }
        }

        /** Keeps every manual line and leaves the screen. */
        fun onKeepDuplicates() {
            duplicateCandidates.value = emptyList()
            viewModelScope.launch { events.send(SalaryProfileEvent.Done) }
        }

        /** Deletes the profile and its managed lines. */
        fun onDelete() {
            viewModelScope.launch {
                manageProfile
                    .delete()
                    .onSuccess { events.send(SalaryProfileEvent.Done) }
                    .onFailure { reportFailure(it) }
            }
        }

        private suspend fun reportFailure(error: Throwable) {
            Log.w(TAG, "Failed to save the salary profile", error)
            events.send(SalaryProfileEvent.Failed)
        }

        private fun contentOf(
            current: SalaryForm,
            saved: Boolean,
            busy: Boolean,
        ): SalaryProfileUiState.Content {
            val result = current.toResult()
            val month = YearMonth.now(clock)
            return SalaryProfileUiState.Content(
                form = current,
                invalid = result.invalid,
                breakdown = salaryBreakdownOf(result.profile).takeIf { result.invalid.isEmpty() },
                canSave = result.hasRequiredAmount && result.invalid.isEmpty() && !busy,
                hasSavedProfile = saved,
                month = month,
                workingDays = workingDaysIn(month),
            )
        }
    }
