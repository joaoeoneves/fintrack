package com.joaoeoneves.fintrack.ui.plan.salary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.data.local.IdGenerator
import com.joaoeoneves.fintrack.domain.model.MAX_SALARY_EXTRAS
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
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
@Suppress("TooManyFunctions") // the screen's form actions plus the extra editor's
@HiltViewModel
class SalaryProfileViewModel
    @Inject
    constructor(
        private val profiles: SalaryProfileRepository,
        private val manageProfile: ManageSalaryProfile,
        private val plannedIncome: PlannedIncomeRepository,
        private val clock: Clock,
        private val idGenerator: IdGenerator,
    ) : ViewModel() {
        // `null` until the stored profile has been read into the form.
        private val form = MutableStateFlow<SalaryForm?>(null)
        private val hasSavedProfile = MutableStateFlow(false)
        private val saving = MutableStateFlow(false)
        private val extraForm = MutableStateFlow<SalaryExtraForm?>(null)
        private var decimalSeparator = '.'
        private val events = Channel<SalaryProfileEvent>(Channel.BUFFERED)

        private val duplicateCandidates = MutableStateFlow<List<PlannedIncome>>(emptyList())

        /** The user's own monthly lines (no earmark) found after a save, to offer removing; empty = no dialog. */
        val duplicates: StateFlow<List<PlannedIncome>> = duplicateCandidates

        val eventFlow: Flow<SalaryProfileEvent> = events.receiveAsFlow()

        val uiState: StateFlow<SalaryProfileUiState> =
            combine(form, hasSavedProfile, saving, extraForm) { current, saved, busy, editing ->
                current?.let { contentOf(it, saved, busy, editing) } ?: SalaryProfileUiState.Loading
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
            }
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
            form.value = current.copy(extras = current.extras.filterNot { it.id == id })
            extraForm.value = null
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
            saved: Boolean,
            busy: Boolean,
            editing: SalaryExtraForm?,
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
                extraEditor = editing?.let { editorStateOf(it, current.mode) },
                canAddExtra = current.extras.size < MAX_SALARY_EXTRAS,
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
