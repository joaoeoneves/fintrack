package com.joaoeoneves.fintrack.ui.settings.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.active
import com.joaoeoneves.fintrack.domain.repository.BuiltInCategoryNames
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.usecase.CategoryUsage
import com.joaoeoneves.fintrack.domain.usecase.CheckCategoryUsage
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

// How long (in ms) the underlying Firestore listener is kept alive after the last collector
// disappears, so a configuration change doesn't tear it down and re-subscribe.
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

@HiltViewModel
class CategoriesViewModel
    @Inject
    constructor(
        private val categoryRepository: CategoryRepository,
        private val checkCategoryUsage: CheckCategoryUsage,
        private val builtInCategoryNames: BuiltInCategoryNames,
    ) : ViewModel() {
        private val editor = MutableStateFlow<CategoryEditorState?>(null)
        private val deleteDialog = MutableStateFlow<DeleteDialogState?>(null)
        private val errors = Channel<UiText>(Channel.BUFFERED)

        // Latest catalog seen by uiState, read by the action handlers.
        @Volatile
        private var catalog: List<Category> = BuiltInCategories.all

        val errorEvent: Flow<UiText> = errors.receiveAsFlow()

        val uiState: StateFlow<CategoriesUiState> =
            combine(
                categoryRepository
                    .observeCategories()
                    .catch { emit(BuiltInCategories.all) }
                    .onEach { catalog = it },
                editor,
                deleteDialog,
            ) { categories, editorState, dialog ->
                CategoriesUiState.Content(
                    active = categories.active(),
                    archived = categories.filter { it.archived },
                    editor = editorState,
                    deleteDialog = dialog,
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
                    categoryRepository.addCategory(draft.name.trim(), draft.iconKey, draft.colorKey, draft.kind)
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

        fun onDeleteRequested(id: CategoryId) {
            val category = catalog.find { it.id == id }?.takeUnless { it.builtIn }
            if (category != null) {
                deleteDialog.value = DeleteDialogState.Checking(category)
                viewModelScope.launch {
                    val result = checkCategoryUsage(id)
                    deleteDialog.update { current ->
                        if (current is DeleteDialogState.Checking && current.category.id == id) {
                            result.fold<DeleteDialogState, CategoryUsage>(
                                onSuccess = { usage ->
                                    if (usage.inUse) {
                                        DeleteDialogState.InUse(category, usage)
                                    } else {
                                        DeleteDialogState.ConfirmDelete(category)
                                    }
                                },
                                onFailure = { DeleteDialogState.CannotVerify(category) },
                            )
                        } else {
                            current
                        }
                    }
                }
            }
        }

        fun onConfirmDelete() {
            val dialog = deleteDialog.value
            if (dialog is DeleteDialogState.ConfirmDelete) {
                deleteDialog.value = null
                launchWrite { categoryRepository.deleteCategory(dialog.category.id) }
            }
        }

        fun onDismissDelete() {
            deleteDialog.value = null
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
