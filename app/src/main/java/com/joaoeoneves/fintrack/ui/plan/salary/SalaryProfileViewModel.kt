package com.joaoeoneves.fintrack.ui.plan.salary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.salaryBreakdownOf
import com.joaoeoneves.fintrack.domain.model.workingDaysIn
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
        private val clock: Clock,
    ) : ViewModel() {
        // `null` until the stored profile has been read into the form.
        private val form = MutableStateFlow<SalaryForm?>(null)
        private val hasSavedProfile = MutableStateFlow(false)
        private val saving = MutableStateFlow(false)
        private val events = Channel<SalaryProfileEvent>(Channel.BUFFERED)

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
                    .onSuccess { events.send(SalaryProfileEvent.Done) }
                    .onFailure { reportFailure(it) }
                saving.value = false
            }
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
