package com.joaoeoneves.fintrack.ui.settings.categories

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.active
import com.joaoeoneves.fintrack.domain.repository.BuiltInCategoryNames
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.usecase.CategoryMovePreview
import com.joaoeoneves.fintrack.domain.usecase.CategoryUsage
import com.joaoeoneves.fintrack.domain.usecase.CheckCategoryUsage
import com.joaoeoneves.fintrack.domain.usecase.MoveCategoryItems
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CategoriesViewModel"

// How long (in ms) the underlying Firestore listener is kept alive after the last collector
// disappears, so a configuration change doesn't tear it down and re-subscribe.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

@Suppress("TooManyFunctions") // One handler per user action on the screen.
@HiltViewModel
class CategoriesViewModel
    @Inject
    constructor(
        private val categoryRepository: CategoryRepository,
        private val checkCategoryUsage: CheckCategoryUsage,
        private val builtInCategoryNames: BuiltInCategoryNames,
        private val moveCategoryItems: MoveCategoryItems,
    ) : ViewModel() {
        private val editor = MutableStateFlow<CategoryEditorState?>(null)
        private val deleteDialog = MutableStateFlow<DeleteDialogState?>(null)
        private val moveDialog = MutableStateFlow<MoveDialogState?>(null)
        private val errors = Channel<UiText>(Channel.BUFFERED)

        // Latest catalog seen by uiState, read by the action handlers.
        @Volatile
        private var catalog: List<Category> = BuiltInCategories.all

        private val deletions = Channel<Category>(Channel.BUFFERED)

        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        /** Each category just deleted, for an undo snackbar. */
        val deletedEvent: Flow<Category> = deletions.receiveAsFlow()

        val uiState: StateFlow<CategoriesUiState> =
            combine(
                categoryRepository
                    .observeCategories()
                    .catch { emit(BuiltInCategories.all) }
                    .onEach { catalog = it },
                editor,
                deleteDialog,
                moveDialog,
            ) { categories, editorState, dialog, move ->
                CategoriesUiState.Content(
                    active = categories.active(),
                    archived = categories.filter { it.archived },
                    editor = editorState,
                    deleteDialog = dialog,
                    moveDialog = move,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                CategoriesUiState.Loading,
            )

        fun onAdd() {
            editor.value = CategoryEditorState(null, false, newCategoryDefaults(catalog))
        }

        fun onEdit(id: CategoryId) {
            catalog.find { it.id == id }?.let { category ->
                editor.value =
                    CategoryEditorState(
                        editingId = id,
                        isBuiltIn = category.builtIn,
                        initial =
                            CategoryDraft(
                                name = category.customName.orEmpty(),
                                iconKey = category.iconKey,
                                colorKey = category.colorKey,
                                kind = category.kind,
                            ),
                    )
            }
        }

        fun onDismissEditor() {
            editor.value = null
        }

        fun onSaveEditor(draft: CategoryDraft) {
            val state = editor.value ?: return
            val existing = state.editingId?.let { id -> catalog.find { it.id == id } }
            val localizedNames = builtInCategoryNames.localizedNames()
            val error = validateCategoryName(draft.name, existing, catalog, localizedNames)
            if (error != null) {
                editor.value = state.copy(nameError = error)
                return
            }
            launchWrite(onSuccess = { editor.value = null }) {
                if (existing == null) {
                    categoryRepository.addCategory(
                        draft.name.trim(),
                        draft.iconKey,
                        draft.colorKey,
                        draft.kind,
                        null,
                    )
                } else {
                    categoryRepository.updateCategory(
                        existing.copy(
                            customName = resolveCustomName(draft.name, existing, localizedNames),
                            iconKey = draft.iconKey,
                            colorKey = draft.colorKey,
                            kind = draft.kind,
                        ),
                    )
                }
            }
        }

        fun onMove(
            id: CategoryId,
            direction: MoveDirection,
        ) {
            val current = catalog
            val newActive = current.moved(id, direction)
            if (newActive != current.active().map { it.id }) {
                val archivedIds = current.filter { it.archived }.map { it.id }
                launchWrite { categoryRepository.reorder(newActive + archivedIds) }
            }
        }

        fun onToggleArchived(id: CategoryId) {
            deleteDialog.value = null
            val category = catalog.find { it.id == id } ?: return
            if (!category.archived && catalog.active().size <= 1) {
                errors.trySend(UiText.StringResource(R.string.error_category_last_active))
            } else {
                launchWrite { categoryRepository.setArchived(id, !category.archived) }
            }
        }

        /** Checks usage, then deletes an unused category at once (the screen offers to undo) or explains why not. */
        fun onDeleteRequested(id: CategoryId) {
            val category = catalog.find { it.id == id }?.takeUnless { it.builtIn }
            if (category != null) {
                deleteDialog.value = DeleteDialogState.Checking(category)
                viewModelScope.launch {
                    val result = checkCategoryUsage(id)
                    val current = deleteDialog.value
                    if (current is DeleteDialogState.Checking && current.category.id == id) {
                        if (result.getOrNull()?.inUse == false) {
                            deleteDialog.value = null
                            deleteUnused(category)
                        } else {
                            deleteDialog.value =
                                result.fold<DeleteDialogState, CategoryUsage>(
                                    onSuccess = { DeleteDialogState.InUse(category, it) },
                                    onFailure = { DeleteDialogState.CannotVerify(category) },
                                )
                        }
                    }
                }
            }
        }

        private suspend fun deleteUnused(category: Category) {
            categoryRepository
                .deleteCategory(category.id)
                .onSuccess { deletions.send(category) }
                .onFailure { e ->
                    Log.w(TAG, "Deleting the category ${category.id.value} failed", e)
                    errors.send(UiText.StringResource(R.string.error_category_save_failed))
                }
        }

        /** Puts back a category deleted a moment ago (same id and position, so its history lines up again). */
        fun onUndoDelete(category: Category) {
            launchWrite { categoryRepository.updateCategory(category) }
        }

        fun onDismissDelete() {
            deleteDialog.value = null
        }

        fun onMoveItemsRequested(id: CategoryId) {
            val category = catalog.find { it.id == id }?.takeIf { it.archived } ?: return
            moveDialog.value = MoveDialogState.Checking(category)
            viewModelScope.launch {
                val result = moveCategoryItems.preview(id)
                moveDialog.update { current ->
                    if (current is MoveDialogState.Checking && current.source.id == id) {
                        result.fold<MoveDialogState, CategoryMovePreview>(
                            onSuccess = { preview ->
                                if (preview.isEmpty) {
                                    MoveDialogState.NothingToMove(category)
                                } else {
                                    MoveDialogState.PickTarget(category, catalog.active().filter { it.id != id })
                                }
                            },
                            onFailure = { MoveDialogState.CannotVerify(category) },
                        )
                    } else {
                        current
                    }
                }
            }
        }

        fun onMoveTargetPicked(target: Category) {
            val dialog = moveDialog.value as? MoveDialogState.PickTarget ?: return
            moveDialog.value = MoveDialogState.Checking(dialog.source)
            viewModelScope.launch {
                moveCategoryItems
                    .preview(dialog.source.id, target.id)
                    .onSuccess { moveDialog.value = MoveDialogState.Confirm(dialog.source, target, it) }
                    .onFailure { moveDialog.value = MoveDialogState.CannotVerify(dialog.source) }
            }
        }

        fun onConfirmMove() {
            val dialog = moveDialog.value as? MoveDialogState.Confirm ?: return
            moveDialog.value = MoveDialogState.Moving(dialog.source, dialog.target)
            viewModelScope.launch {
                moveCategoryItems(dialog.source.id, dialog.target.id)
                    .onSuccess {
                        moveDialog.value = null
                        // Shown as a snackbar like the errors, which share this channel.
                        errors.send(UiText.StringResource(R.string.category_move_done))
                    }.onFailure { e ->
                        Log.w(TAG, "Moving the items of ${dialog.source.id.value} failed", e)
                        moveDialog.value = null
                        errors.send(UiText.StringResource(R.string.error_category_move_failed))
                    }
            }
        }

        fun onDismissMove() {
            // A running move can't be cancelled from the dialog; it finishes and reports on its own.
            if (moveDialog.value !is MoveDialogState.Moving) moveDialog.value = null
        }

        private fun launchWrite(
            onSuccess: () -> Unit = {},
            block: suspend () -> Result<*>,
        ) {
            viewModelScope.launch {
                block()
                    .onSuccess { onSuccess() }
                    .onFailure { errors.send(UiText.StringResource(R.string.error_category_save_failed)) }
            }
        }
    }
