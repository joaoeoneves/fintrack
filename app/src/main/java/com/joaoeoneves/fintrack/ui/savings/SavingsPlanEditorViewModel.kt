package com.joaoeoneves.fintrack.ui.savings

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.navigation.EditSavingsPlan
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "SavingsPlanEditorVM"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val DAY_MAX_DIGITS = 2

sealed interface SavingsPlanEditorUiState {
    data object Loading : SavingsPlanEditorUiState

    data object NotFound : SavingsPlanEditorUiState

    /** [categories] are the SAVING-kind categories the monthly amount can go to. */
    data class Content(
        val draft: SavingsPlanDraft,
        val categories: List<Category>,
        val isNew: Boolean,
        val saving: Boolean,
    ) : SavingsPlanEditorUiState
}

/**
 * Creates, edits and deletes one savings plan (the id comes from the [EditSavingsPlan] route). The draft is seeded
 * once so repository updates never overwrite what the user typed.
 */
@Suppress("TooManyFunctions") // One handler per field of the editor.
@HiltViewModel
class SavingsPlanEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val goals: GoalRepository,
        private val recurring: RecurringRepository,
        categories: CategoryRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val planId = savedStateHandle.toRoute<EditSavingsPlan>().id
        private val writer = SavingsPlanWriter(goals, recurring)
        private val draft = MutableStateFlow<SavingsPlanDraft?>(null)
        private val notFound = MutableStateFlow(false)
        private val saving = MutableStateFlow(false)
        private val errors = Channel<UiText>(Channel.BUFFERED)
        private val back = Channel<Unit>(Channel.BUFFERED)
        private val deleted = Channel<DeletedSavingsPlan>(Channel.BUFFERED)

        @Volatile
        private var original: SavingsPlanDraft? = null

        /** One-shot messages for writes that failed, shown as a snackbar. */
        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        /** Emits once after a successful save. */
        val navigateBack: Flow<Unit> = back.receiveAsFlow()

        /** Emits once after a successful delete, with what the app-level undo needs. */
        val deletedEvent: Flow<DeletedSavingsPlan> = deleted.receiveAsFlow()

        val uiState: StateFlow<SavingsPlanEditorUiState> =
            combine(draft, categories.observeCategories(), notFound, saving) { current, catalog, missing, busy ->
                when {
                    current == null ->
                        if (missing) SavingsPlanEditorUiState.NotFound else SavingsPlanEditorUiState.Loading
                    else ->
                        SavingsPlanEditorUiState.Content(
                            draft = current,
                            categories = savingCategories(catalog, current.categoryId),
                            isNew = planId == null,
                            saving = busy,
                        )
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                SavingsPlanEditorUiState.Loading,
            )

        init {
            viewModelScope.launch { seed() }
        }

        private suspend fun seed() {
            runCatching {
                val today = LocalDate.now(clock)
                val stored = goals.observeGoals().first().firstOrNull { it.id == planId }
                if (planId != null && stored == null) {
                    notFound.value = true
                } else {
                    val item = stored?.ownedItem(recurring.observeRecurring().first())
                    val seeded = draftOf(stored, item, today)
                    original = seeded
                    draft.value = seeded
                }
            }.onFailure {
                Log.w(TAG, "Failed to load the savings plan", it)
                notFound.value = true
            }
        }

        fun onNameChange(name: String) = edit { it.copy(name = limitInput(it.name, name, MAX_GOAL_NAME_LENGTH)) }

        fun onMonthlyChange(cents: Long) = edit { it.copy(monthlyCents = cents) }

        fun onDayChange(text: String) = edit { it.copy(dayText = text.filter(Char::isDigit).take(DAY_MAX_DIGITS)) }

        fun onCategoryChange(id: CategoryId) = edit { it.copy(categoryId = id) }

        fun onAutoPostChange(enabled: Boolean) = edit { it.copy(autoPost = enabled) }

        fun onTargetChange(cents: Long) = edit { it.copy(targetCents = cents) }

        fun onTypeChange(type: GoalTargetType) = edit { it.copy(type = type) }

        fun onTargetDateChange(date: LocalDate?) = edit { it.copy(targetDate = date) }

        fun onInitialChange(cents: Long) = edit { it.copy(initialCents = cents) }

        private fun edit(transform: (SavingsPlanDraft) -> SavingsPlanDraft) = draft.update { it?.let(transform) }

        /** Validates the draft and writes the plan (monthly item first, then the goal); navigates back on success. */
        fun onSave() {
            val current = draft.value
            if (current == null || saving.value) return
            if (!current.valid) {
                draft.update { it?.copy(showErrors = true) }
                return
            }
            saving.value = true
            viewModelScope.launch {
                runCatching { storedPlan() }
                    .mapCatching { writer.save(current, it, LocalDate.now(clock)).getOrThrow() }
                    .onSuccess { back.send(Unit) }
                    .onFailure {
                        Log.w(TAG, "Failed to save the savings plan", it)
                        errors.send(UiText.StringResource(R.string.error_goal_save_failed))
                    }
                saving.value = false
            }
        }

        /** Deletes the plan (the caller has confirmed): its monthly item first, then the goal; offers undo. */
        fun onDelete() {
            if (planId == null) return
            viewModelScope.launch {
                runCatching { storedPlan() }
                    .mapCatching { stored ->
                        writer.delete(stored).getOrThrow()
                        DeletedSavingsPlan(requireNotNull(stored.goal), stored.item)
                    }.onSuccess { deleted.send(it) }
                    .onFailure {
                        Log.w(TAG, "Failed to delete the savings plan", it)
                        errors.send(UiText.StringResource(R.string.error_goal_save_failed))
                    }
            }
        }

        private suspend fun storedPlan(): StoredPlan {
            val all = goals.observeGoals().first()
            val goal = all.firstOrNull { it.id == planId }
            val item = goal?.ownedItem(recurring.observeRecurring().first())
            return StoredPlan(goal, item, original, (all.maxOfOrNull { it.order } ?: -1) + 1)
        }
    }

private fun savingCategories(
    catalog: List<Category>,
    selected: CategoryId?,
): List<Category> =
    catalog
        .filter { (it.kind == CategoryKind.SAVING && !it.archived && !it.isUnknown) || it.id == selected }
        .ifEmpty { listOf(BuiltInCategories.INVESTMENTS) }
