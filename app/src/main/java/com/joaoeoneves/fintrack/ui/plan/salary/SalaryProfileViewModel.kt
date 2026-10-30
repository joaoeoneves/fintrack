package com.joaoeoneves.fintrack.ui.plan.salary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.data.local.IdGenerator
import com.joaoeoneves.fintrack.domain.model.MAX_SALARY_EXTRAS
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.YearEstimateInput
import com.joaoeoneves.fintrack.domain.model.defaultEffectiveFrom
import com.joaoeoneves.fintrack.domain.model.salaryBreakdownOf
import com.joaoeoneves.fintrack.domain.model.salaryProfileLines
import com.joaoeoneves.fintrack.domain.model.salaryYearEstimateOf
import com.joaoeoneves.fintrack.domain.model.vacationBalanceOf
import com.joaoeoneves.fintrack.domain.model.vacationDays
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
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

private class FormState(
    val form: SalaryForm?,
    val saved: Boolean,
    val busy: Boolean,
    val editing: SalaryExtraForm?,
)

/** The stored plan the year estimate is worked out from: the income lines and each month's days off. */
private class StoredPlan(
    val lines: List<PlannedIncome>,
    val settings: List<MonthSettings>,
)

/** Edits the salary profile and keeps the plan's managed income lines in sync with it. */
@Suppress("TooManyFunctions") // the screen's form actions plus the extra editor's
@HiltViewModel
class SalaryProfileViewModel
    @Inject
    constructor(
        private val profiles: SalaryProfileRepository,
        private val manageProfile: ManageSalaryProfile,
        private val plannedIncome: PlannedIncomeRepository,
        private val monthSettings: MonthSettingsRepository,
        private val clock: Clock,
        private val idGenerator: IdGenerator,
    ) : ViewModel() {
        // `null` until the stored profile has been read into the form.
        private val form = MutableStateFlow<SalaryForm?>(null)
        private val hasSavedProfile = MutableStateFlow(false)
        private val saving = MutableStateFlow(false)
        private val extraForm = MutableStateFlow<SalaryExtraForm?>(null)
        private val effectiveFrom = MutableStateFlow<YearMonth?>(null)

        // The stored profile as the form round-trips it, to tell whether the user changed anything.
        private var originalProfile: SalaryProfile? = null
        private var decimalSeparator = '.'
        private val events = Channel<SalaryProfileEvent>(Channel.BUFFERED)

        private val duplicateCandidates = MutableStateFlow<List<PlannedIncome>>(emptyList())

        /** The user's own monthly lines found after a save, to offer removing; empty = no dialog. */
        val duplicates: StateFlow<List<PlannedIncome>> = duplicateCandidates

        /** Whether the form being saved has a meal allowance (the duplicates dialog mentions it only then). */
        val hasMeal: Boolean get() = (form.value?.meal ?: 0L) > 0L

        val eventFlow: Flow<SalaryProfileEvent> = events.receiveAsFlow()

        val uiState: StateFlow<SalaryProfileUiState> =
            combine(
                combine(form, hasSavedProfile, saving, extraForm, ::FormState),
                effectiveFrom,
                combine(plannedIncome.observePlannedIncome(), monthSettings.observeMonthSettings(), ::StoredPlan),
            ) { typed, from, plan ->
                typed.form?.let { contentOf(it, typed, from, plan) } ?: SalaryProfileUiState.Loading
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = SalaryProfileUiState.Loading,
            )

        /** Loads the stored profile into the form once; amounts are formatted with [decimalSeparator]. */
        fun start(decimalSeparator: Char) {
            if (form.value != null) return
            this.decimalSeparator = decimalSeparator
            viewModelScope.launch {
                val stored = profiles.observeSalaryProfile().first()
                hasSavedProfile.value = stored != null
                form.value = stored?.toForm(decimalSeparator) ?: SalaryForm()
                originalProfile = stored?.let { form.value?.toResult()?.profile }
            }
        }

        fun onEffectiveFromChange(month: YearMonth) {
            effectiveFrom.value = month
        }

        fun onFormChange(new: SalaryForm) {
            form.value = new
        }

        /** Opens the editor for a new extra, paid in the current month; a no-op at the cap. */
        fun onAddExtra() {
            val current = form.value ?: return
            if (current.extras.size >= MAX_SALARY_EXTRAS) return
            extraForm.value = SalaryExtraForm(months = listOf(YearMonth.now(clock).monthValue))
        }

        /** Opens the editor for extra [id]; in NET mode only a plain amount exists, so another type starts blank. */
        fun onEditExtra(id: String) {
            val current = form.value ?: return
            val extra = current.extras.find { it.id == id } ?: return
            val editing = extra.toExtraForm(decimalSeparator)
            extraForm.value =
                if (current.mode == SalaryInputMode.NET && extra.amountType != SalaryExtraAmountType.FIXED_CENTS) {
                    editing.copy(amountType = SalaryExtraAmountType.FIXED_CENTS, amountText = "", amountCents = 0L)
                } else {
                    editing
                }
        }

        fun onExtraFormChange(new: SalaryExtraForm) {
            extraForm.value = new
        }

        fun onDismissExtra() {
            extraForm.value = null
        }

        /** Adds the extra being edited (or replaces it in place); it is stored with the screen's Save. */
        fun onSaveExtra() {
            val current = form.value
            val editing = extraForm.value
            val extra = editing?.toExtra(editing.editingId ?: idGenerator.newId(), current?.mode ?: return)?.first
            if (current == null || editing == null || extra == null) return
            val extras =
                if (editing.editingId == null) {
                    current.extras + extra
                } else {
                    current.extras.map { if (it.id == extra.id) extra else it }
                }
            form.value = current.copy(extras = extras)
            extraForm.value = null
        }

        fun onDeleteExtra(id: String) {
            val current = form.value ?: return
            val index = current.extras.indexOfFirst { it.id == id }
            if (index < 0) return
            form.value = current.copy(extras = current.extras.filterNot { it.id == id })
            extraForm.value = null
            events.trySend(SalaryProfileEvent.ExtraDeleted(current.extras[index], index))
        }

        /** Puts a just-deleted draft extra back where it was. */
        fun onUndoDeleteExtra(
            extra: SalaryExtra,
            index: Int,
        ) {
            val current = form.value ?: return
            if (current.extras.any { it.id == extra.id }) return
            val at = index.coerceIn(0, current.extras.size)
            form.value = current.copy(extras = current.extras.take(at) + extra + current.extras.drop(at))
        }

        /** Stores the profile and regenerates the managed lines, naming new ones with [labels]. */
        fun onSave(labels: SalaryLineLabels) {
            val current = form.value ?: return
            val result = current.toResult()
            if (saving.value || !result.hasRequiredAmount || result.invalid.isNotEmpty()) return
            viewModelScope.launch {
                saving.value = true
                manageProfile
                    .save(
                        result.profile,
                        labels,
                        effectiveFrom.value ?: defaultEffectiveFrom(YearMonth.now(clock), current.raiseInFebruary),
                    ).onSuccess { finishSave() }
                    .onFailure { reportFailure(it) }
                saving.value = false
            }
        }

        // Leaves the screen, unless manual monthly lines may now be counted twice: then asks about them first.
        private suspend fun finishSave() {
            val others = manualMonthlyLines(plannedIncome)
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
            typed: FormState,
            from: YearMonth?,
            plan: StoredPlan,
        ): SalaryProfileUiState.Content {
            val (saved, busy, editing) = Triple(typed.saved, typed.busy, typed.editing)
            val result = current.toResult()
            val month = YearMonth.now(clock)
            val effective = from ?: defaultEffectiveFrom(month, current.raiseInFebruary)
            val breakdown = salaryBreakdownOf(result.profile).takeIf { result.invalid.isEmpty() }
            val vacation = result.profile.vacationDays
            return SalaryProfileUiState.Content(
                form = current,
                invalid = result.invalid,
                breakdown = breakdown,
                canSave = result.hasRequiredAmount && result.invalid.isEmpty() && !busy,
                hasSavedProfile = saved,
                month = month,
                yearSummary =
                    breakdown?.let {
                        // The lines this profile would produce (honouring "valid from", versions and the start month),
                        // added up month by month like the Income Year view.
                        val lines = salaryProfileLines(result.profile, plan.lines, effectiveFrom = effective)
                        val input = YearEstimateInput(lines, plan.settings, vacation)
                        salaryYearEstimateOf(result.profile, it, month.year, input)
                    },
                vacationDaysAssumed = vacation,
                vacationBalance = vacationBalanceOf(month.year, plan.settings, result.profile),
                extraEditor = editing?.let { editorStateOf(it, current.mode) },
                canAddExtra = current.extras.size < MAX_SALARY_EXTRAS,
                effectiveFrom = effective,
                showEffectiveFrom = !saved || result.profile != originalProfile,
            )
        }

        private fun editorStateOf(
            editing: SalaryExtraForm,
            mode: SalaryInputMode,
        ): SalaryExtraEditorState {
            val (extra, invalid) = editing.toExtra(editing.editingId.orEmpty(), mode)
            return SalaryExtraEditorState(editing, invalid, canSave = extra != null, mode = mode)
        }
    }
