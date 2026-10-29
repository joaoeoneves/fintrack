package com.joaoeoneves.fintrack.ui.plan.salary.setup

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryBreakdown
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.salaryBreakdownOf
import com.joaoeoneves.fintrack.domain.model.workingDaysIn
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import com.joaoeoneves.fintrack.domain.usecase.ManageSalaryProfile
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryField
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import com.joaoeoneves.fintrack.ui.plan.salary.manualMonthlyLines
import com.joaoeoneves.fintrack.ui.plan.salary.toResult
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

private const val TAG = "SalarySetupViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val KEY_STEP = "step"
private const val KEY_LUMPS = "net_lumps"

/** The steps of the salary set-up wizard, in order. */
enum class SalarySetupStep { PAY, SUBSIDIES, MEAL, SUMMARY }

private class SetupInputs(
    val step: SalarySetupStep,
    val form: SalaryForm,
    val lumps: Boolean,
    val busy: Boolean,
)

/** What the set-up wizard shows; [stepNumber] of [stepCount] is 1-based. */
data class SalarySetupUiState(
    val step: SalarySetupStep,
    val form: SalaryForm,
    /** Net mode: whether the subsidies come as separate lump sums instead of in twelfths with the salary. */
    val netLumps: Boolean,
    val invalid: Set<SalaryField>,
    val breakdown: SalaryBreakdown?,
    val month: YearMonth,
    val workingDays: Int,
    val canContinue: Boolean,
    val saving: Boolean,
    /** The plan month the new salary lines start in (default: the current month). */
    val effectiveFrom: YearMonth = month,
) {
    val stepNumber: Int get() = step.ordinal + 1
    val stepCount: Int get() = SalarySetupStep.entries.size
}

/** One-off outcomes of the wizard. */
sealed interface SalarySetupEvent {
    data object Done : SalarySetupEvent

    data object Failed : SalarySetupEvent

    /** A profile already exists: the wizard never overwrites it, so the full editor opens instead. */
    data object OpenAdvanced : SalarySetupEvent
}

/** Fills the same [SalaryForm] as the salary profile editor, in three short steps plus a summary. */
@Suppress("TooManyFunctions") // one handler per wizard action, plus the duplicate-line dialog's
@HiltViewModel
class SalarySetupViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val profiles: SalaryProfileRepository,
        private val manageProfile: ManageSalaryProfile,
        private val plannedIncome: PlannedIncomeRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val step = MutableStateFlow(storedStep(savedState))
        private val form = MutableStateFlow(SalaryForm())
        private val netLumps = MutableStateFlow(savedState.get<Boolean>(KEY_LUMPS) ?: false)
        private val saving = MutableStateFlow(false)
        private val effectiveFrom = MutableStateFlow<YearMonth?>(null)
        private val duplicateCandidates = MutableStateFlow<List<PlannedIncome>>(emptyList())
        private val events = Channel<SalarySetupEvent>(Channel.BUFFERED)

        /** The user's own monthly lines found after a save, to offer removing; empty means no dialog. */
        val duplicates: StateFlow<List<PlannedIncome>> = duplicateCandidates

        val eventFlow: Flow<SalarySetupEvent> = events.receiveAsFlow()

        val uiState: StateFlow<SalarySetupUiState> =
            combine(
                combine(step, form, netLumps, saving, ::SetupInputs),
                effectiveFrom,
            ) { inputs, from ->
                stateOf(inputs, from)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = stateOf(SetupInputs(step.value, form.value, netLumps.value, saving.value), null),
            )

        init {
            viewModelScope.launch {
                if (profiles.observeSalaryProfile().first() != null) events.send(SalarySetupEvent.OpenAdvanced)
            }
        }

        fun onEffectiveFromChange(month: YearMonth) {
            effectiveFrom.value = month
        }

        fun onFormChange(new: SalaryForm) {
            form.value = new
        }

        fun onNetLumpsChange(separate: Boolean) {
            netLumps.value = separate
            savedState[KEY_LUMPS] = separate
            if (!separate) form.value = form.value.copy(netHoliday = 0L, netChristmas = 0L)
        }

        fun onNext() {
            val current = uiState.value
            if (!current.canContinue) return
            val next = SalarySetupStep.entries.getOrNull(current.step.ordinal + 1) ?: return
            moveTo(next)
        }

        /** Goes back one step; returns false on the first step, where the screen should leave instead. */
        fun onBack(): Boolean {
            val previous = SalarySetupStep.entries.getOrNull(step.value.ordinal - 1) ?: return false
            moveTo(previous)
            return true
        }

        /** Stores the profile and regenerates the managed lines, naming new ones with [labels]. */
        fun onSave(labels: SalaryLineLabels) {
            val result = form.value.toResult()
            if (saving.value || !result.hasRequiredAmount || result.invalid.isNotEmpty()) return
            viewModelScope.launch {
                saving.value = true
                manageProfile
                    .save(result.profile, labels, effectiveFrom.value ?: YearMonth.now(clock))
                    .onSuccess { finishSave() }
                    .onFailure {
                        Log.w(TAG, "Failed to save the salary profile", it)
                        events.send(SalarySetupEvent.Failed)
                    }
                saving.value = false
            }
        }

        /** Deletes the lines with [ids] (the ones the user ticked) and leaves the wizard. */
        fun onRemoveDuplicates(ids: Set<String>) {
            viewModelScope.launch {
                val failed = ids.map { plannedIncome.deletePlannedIncome(it) }.any { it.isFailure }
                duplicateCandidates.value = emptyList()
                events.send(if (failed) SalarySetupEvent.Failed else SalarySetupEvent.Done)
            }
        }

        /** Keeps every manual line and leaves the wizard. */
        fun onKeepDuplicates() {
            duplicateCandidates.value = emptyList()
            viewModelScope.launch { events.send(SalarySetupEvent.Done) }
        }

        private suspend fun finishSave() {
            val others = manualMonthlyLines(plannedIncome)
            if (others.isEmpty()) events.send(SalarySetupEvent.Done) else duplicateCandidates.value = others
        }

        private fun moveTo(next: SalarySetupStep) {
            step.value = next
            savedState[KEY_STEP] = next.name
        }

        private fun stateOf(
            inputs: SetupInputs,
            from: YearMonth?,
        ): SalarySetupUiState {
            val current = inputs.step
            val typed = inputs.form
            val lumps = inputs.lumps
            val busy = inputs.busy
            val result = typed.toResult()
            val month = YearMonth.now(clock)
            val effective = from ?: month
            val valid = result.invalid.isEmpty()
            return SalarySetupUiState(
                step = current,
                form = typed,
                netLumps = lumps,
                invalid = result.invalid,
                breakdown = salaryBreakdownOf(result.profile).takeIf { valid },
                month = month,
                workingDays = workingDaysIn(month),
                canContinue = valid && !busy && (current != SalarySetupStep.PAY || result.hasRequiredAmount),
                saving = busy,
                effectiveFrom = effective,
            )
        }
    }

private fun storedStep(savedState: SavedStateHandle): SalarySetupStep =
    SalarySetupStep.entries.firstOrNull { it.name == savedState.get<String>(KEY_STEP) } ?: SalarySetupStep.PAY

/** Whether [mode] is the gross one; the wizard's pay step and the subsidies step differ by it. */
internal val SalaryForm.isGross: Boolean get() = mode == SalaryInputMode.GROSS
