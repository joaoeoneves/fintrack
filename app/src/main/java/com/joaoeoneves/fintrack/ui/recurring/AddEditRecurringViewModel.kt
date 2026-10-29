package com.joaoeoneves.fintrack.ui.recurring

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.MAX_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.MAX_NOTE_LENGTH
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.domain.model.resolve
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.ui.common.FormSnapshotStore
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.navigation.AddEditRecurring
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "AddEditRecurringVM"
private const val FORM_KEY = "recurring_form"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Creates, edits and deletes one recurring item (the id comes from the [AddEditRecurring] route). */
@HiltViewModel
class AddEditRecurringViewModel
    @Inject
    constructor(
        private val recurringRepository: RecurringRepository,
        private val categoryRepository: CategoryRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<AddEditRecurring>()

        private val snapshots = FormSnapshotStore(savedStateHandle, FORM_KEY, RecurringFormSnapshot.serializer())

        val isEditRoute: Boolean = route.recurringId != null

        private val _uiState = MutableStateFlow<AddEditRecurringUiState>(AddEditRecurringUiState.Loading)
        val uiState: StateFlow<AddEditRecurringUiState> = _uiState.asStateFlow()

        /** The full category catalog (archived included); the picker offers its active entries. */
        val categoryOptions: StateFlow<List<Category>> =
            categoryRepository
                .observeCategories()
                .catch { emit(BuiltInCategories.all) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), BuiltInCategories.all)

        private val savedEvents = Channel<Unit>(Channel.BUFFERED)

        /** Emits once the item was saved or deleted, so the screen can close. */
        val savedEvent: Flow<Unit> = savedEvents.receiveAsFlow()

        private val deletions = Channel<RecurringItem>(Channel.BUFFERED)

        /** Emits the item just deleted, for the app-level undo snackbar; the screen closes after it. */
        val deletedEvent: Flow<RecurringItem> = deletions.receiveAsFlow()

        // The item being edited, kept so saving preserves its cursor and active flag.
        private var existing: RecurringItem? = null

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value = AddEditRecurringUiState.Loading
                val catalog = categoryRepository.observeCategories().catch { emit(BuiltInCategories.all) }.first()
                val id = route.recurringId
                _uiState.value =
                    if (id == null) {
                        // No preselected category: recurrence isn't a category, so the user must choose one.
                        AddEditRecurringUiState.Ready(saved(catalog) ?: newRecurringForm(LocalDate.now(clock)))
                    } else {
                        loadExisting(id, catalog)
                    }
            }
        }

        private suspend fun loadExisting(
            id: String,
            catalog: List<Category>,
        ): AddEditRecurringUiState {
            val items =
                recurringRepository
                    .observeRecurring()
                    .map { Result.success(it) }
                    .catch { emit(Result.failure(it)) }
                    .first()
            val item = items.getOrNull()?.firstOrNull { it.id == id }
            items.exceptionOrNull()?.let { Log.w(TAG, "Failed to load recurring item $id", it) }
            return when {
                items.isFailure -> AddEditRecurringUiState.Error(UiText.StringResource(R.string.error_generic_fallback))
                item == null -> AddEditRecurringUiState.Error(UiText.StringResource(R.string.error_recurring_not_found))
                else -> {
                    existing = item
                    val category = item.categoryId?.let { catalog.resolve(it) }
                    AddEditRecurringUiState.Ready(saved(catalog) ?: item.toForm(category), isEditMode = true)
                }
            }
        }

        /** What was typed before the process died, when it belongs to this item. */
        private fun saved(catalog: List<Category>): RecurringForm? =
            snapshots.read()?.toForm(route.recurringId, catalog)

        fun onRetry() = load()

        /** Applies an edit; name and note are capped at their rule limits, and stale errors cleared. */
        fun onFormChanged(form: RecurringForm) {
            val current = _uiState.value
            if (current is AddEditRecurringUiState.Ready) {
                val limited =
                    form.copy(
                        name = limitInput(current.form.name, form.name, MAX_NAME_LENGTH),
                        note = limitInput(current.form.note, form.note, MAX_NOTE_LENGTH),
                    )
                snapshots.write(limited.toSnapshot(route.recurringId))
                _uiState.value = current.copy(form = limited, errors = null, saveError = null)
            }
        }

        fun onSave() {
            val current = _uiState.value
            if (current !is AddEditRecurringUiState.Ready || current.isSaving) return
            val errors = validateRecurringForm(current.form)
            if (errors != null) {
                _uiState.value = current.copy(errors = errors)
                return
            }
            _uiState.value = current.copy(isSaving = true, errors = null, saveError = null)
            viewModelScope.launch {
                recurringRepository
                    .saveRecurring(current.form.toItem(existing))
                    .onSuccess { savedEvents.send(Unit) }
                    .onFailure { e -> reportFailure("save", e) }
            }
        }

        fun onDelete() {
            val current = _uiState.value
            val item = existing
            if (current !is AddEditRecurringUiState.Ready || current.isSaving || item == null) return
            _uiState.value = current.copy(isSaving = true, saveError = null)
            viewModelScope.launch {
                recurringRepository
                    .deleteRecurring(item.id)
                    .onSuccess { deletions.send(item) }
                    .onFailure { e -> reportFailure("delete", e) }
            }
        }

        private fun reportFailure(
            action: String,
            error: Throwable,
        ) {
            Log.w(TAG, "Failed to $action recurring item", error)
            val latest = _uiState.value
            if (latest is AddEditRecurringUiState.Ready) {
                val message = UiText.StringResource(R.string.error_recurring_save_failed)
                _uiState.value = latest.copy(isSaving = false, saveError = message)
            }
        }
    }
