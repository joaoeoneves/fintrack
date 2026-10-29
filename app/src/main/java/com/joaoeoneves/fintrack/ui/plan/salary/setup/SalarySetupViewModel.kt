package com.joaoeoneves.fintrack.ui.plan.salary.setup

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryBreakdown
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.isManaged
import com.joaoeoneves.fintrack.domain.model.salaryBreakdownOf
import com.joaoeoneves.fintrack.domain.model.workingDaysIn
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import com.joaoeoneves.fintrack.domain.usecase.ManageSalaryProfile
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryField
import com.joaoeoneves.fintrack.ui.plan.salary.SalaryForm
import com.joaoeoneves.fintrack.ui.plan.salary.effectiveSector
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
private const val KEY_SECTOR = "sector"

private class SetupChoices(
    val mealAnswer: MealAnswer,
    val payDay: PayDayDraft,
    val irsJovem: Boolean,
)

private class SetupInputs(
    val step: SalarySetupStep,
    val form: SalaryForm,
    val lumps: Boolean,
    val busy: Boolean,
    val choices: SetupChoices,
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
    val mealAnswer: MealAnswer = MealAnswer.CARD,
    val payDay: PayDayDraft = PayDayDraft(),
    /** Whether the "I have IRS Jovem" switch is on (it reveals the exempt-percent field). */
    val irsJovem: Boolean = false,
) {
    val steps: List<SalarySetupStep> get() = setupStepsFor(form.sector)
    val stepNumber: Int get() = steps.indexOf(step) + 1
    val stepCount: Int get() = steps.size
    val sector: EmploymentSector get() = form.effectiveSector
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
        private val restoredSector = storedSector(savedState)
        private val draft = savedState.loadDraft()
        private val form =
            MutableStateFlow(
                draft?.form ?: restoredSector?.let { SalaryForm().withSectorPreset(it) } ?: SalaryForm(),
            )
        private val mealAnswer =
            MutableStateFlow(draft?.mealAnswer ?: restoredSector?.let(::mealAnswerPresetFor) ?: MealAnswer.CARD)
        private val payDay = MutableStateFlow(draft?.payDay ?: restoredSector?.let(::payDayPresetFor) ?: PayDayDraft())
        private val irsJovem = MutableStateFlow(draft?.irsJovem ?: false)
        private val netLumps = MutableStateFlow(savedState.get<Boolean>(KEY_LUMPS) ?: false)
        private val saving = MutableStateFlow(false)
        private val effectiveFrom = MutableStateFlow(draft?.effectiveFrom)
        private val duplicateCandidates = MutableStateFlow<List<PlannedIncome>>(emptyList())
        private val events = Channel<SalarySetupEvent>(Channel.BUFFERED)

        /** The user's own monthly lines found after a save, to offer removing; empty means no dialog. */
        val duplicates: StateFlow<List<PlannedIncome>> = duplicateCandidates

        /** Whether the form being saved has a meal allowance (the duplicates dialog mentions it only then). */
        val hasMeal: Boolean get() = form.value.meal > 0L

        val eventFlow: Flow<SalarySetupEvent> = events.receiveAsFlow()

        val uiState: StateFlow<SalarySetupUiState> =
            combine(
                combine(
                    step,
                    form,
                    netLumps,
                    saving,
                    combine(mealAnswer, payDay, irsJovem, ::SetupChoices),
                    ::SetupInputs,
                ),
                effectiveFrom,
            ) { inputs, from ->
                stateOf(inputs, from)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = stateOf(currentInputs(), null),
            )

        init {
            viewModelScope.launch {
                combine(form, mealAnswer, payDay, irsJovem, effectiveFrom, ::SetupDraft).collect(savedState::saveDraft)
            }
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

        /** Picks where the user works: applies that sector's presets and moves to the next step. */
        fun onSectorSelected(sector: EmploymentSector) {
            if (form.value.sector != sector) {
                val answers = SectorAnswers(form.value, mealAnswer.value, payDay.value).withSector(sector)
                form.value = answers.form
                mealAnswer.value = answers.mealAnswer
                payDay.value = answers.payDay
                irsJovem.value = form.value.exempt.isNotBlank()
                savedState[KEY_SECTOR] = sector.name
            }
            moveTo(SalarySetupStep.PAY)
        }

        fun onMealAnswerChange(answer: MealAnswer) {
            mealAnswer.value = answer
            form.value = form.value.withMealAnswer(answer)
        }

        fun onPayDayChange(draft: PayDayDraft) {
            payDay.value = draft.copy(dayText = draft.dayText.filter(Char::isDigit).take(2))
        }

        /** The "I have IRS Jovem" switch; turning it off clears the exempt percent. */
        fun onIrsJovemChange(on: Boolean) {
            irsJovem.value = on
            if (!on) form.value = form.value.copy(exempt = "")
        }

        /** The "I have ADSE" switch: sets other deductions to 3.5% (typed with [decimalSeparator]), or clears them. */
        fun onAdseChange(
            on: Boolean,
            decimalSeparator: Char,
        ) {
            form.value = form.value.withAdse(on, decimalSeparator)
        }

        fun onNetLumpsChange(separate: Boolean) {
            netLumps.value = separate
            savedState[KEY_LUMPS] = separate
            if (!separate) form.value = form.value.copy(netHoliday = 0L, netChristmas = 0L)
        }

        fun onNext() {
            val current = uiState.value
            if (!current.canContinue) return
            val next = current.steps.getOrNull(current.steps.indexOf(current.step) + 1) ?: return
            moveTo(next)
        }

        /** Goes back one step; returns false on the first step, where the screen should leave instead. */
        fun onBack(): Boolean {
            val steps = setupStepsFor(form.value.sector)
            val previous = steps.getOrNull(steps.indexOf(step.value) - 1) ?: return false
            moveTo(previous)
            return true
        }

        /** Stores the profile and regenerates the managed lines, naming new ones with [labels]. */
        fun onSave(labels: SalaryLineLabels) {
            val result = form.value.toResult()
            val pay = payDay.value
            val savable = result.hasRequiredAmount && result.invalid.isEmpty() && pay.isValid
            if (saving.value || !savable) return
            viewModelScope.launch {
                saving.value = true
                manageProfile
                    .save(result.profile, labels, effectiveFrom.value ?: YearMonth.now(clock))
                    .mapCatching { applyPayDay(pay) }
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

        // The new profile has no managed lines of its own yet, so the chosen pay day goes on every one of them.
        private suspend fun applyPayDay(pay: PayDayDraft) {
            val day = pay.day ?: return
            plannedIncome
                .observePlannedIncome()
                .first()
                .filter { it.isManaged }
                .forEach {
                    plannedIncome
                        .savePlannedIncome(it.copy(payDay = day, payDayEarlierIfClosed = pay.earlierIfClosed))
                        .getOrThrow()
                }
        }

        private fun currentInputs() =
            SetupInputs(
                step.value,
                form.value,
                netLumps.value,
                saving.value,
                SetupChoices(mealAnswer.value, payDay.value, irsJovem.value),
            )

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
                canContinue =
                    valid &&
                        !busy &&
                        (current == SalarySetupStep.SECTOR || result.hasRequiredAmount) &&
                        (current != SalarySetupStep.SUMMARY || inputs.choices.payDay.isValid),
                saving = busy,
                effectiveFrom = effective,
                mealAnswer = inputs.choices.mealAnswer,
                payDay = inputs.choices.payDay,
                irsJovem = inputs.choices.irsJovem,
            )
        }
    }

private fun storedStep(savedState: SavedStateHandle): SalarySetupStep =
    SalarySetupStep.entries
        .firstOrNull { it.name == savedState.get<String>(KEY_STEP) }
        // Past the first step only with a sector chosen; without one the wizard starts over.
        ?.takeIf { it == SalarySetupStep.SECTOR || storedSector(savedState) != null }
        ?: SalarySetupStep.SECTOR

private fun storedSector(savedState: SavedStateHandle): EmploymentSector? =
    EmploymentSector.entries.firstOrNull { it.name == savedState.get<String>(KEY_SECTOR) }

/** Whether [mode] is the gross one; the wizard's pay step and the subsidies step differ by it. */
internal val SalaryForm.isGross: Boolean get() = mode == SalaryInputMode.GROSS
