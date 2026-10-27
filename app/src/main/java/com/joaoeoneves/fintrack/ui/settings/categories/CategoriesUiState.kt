package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.usecase.CategoryUsage
import com.joaoeoneves.fintrack.ui.common.UiText

sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState

    data class Content(
        val active: List<Category>,
        val archived: List<Category>,
        val editor: CategoryEditorState?,
        val deleteDialog: DeleteDialogState?,
    ) : CategoriesUiState
}

/** The editable fields of the category editor dialog. */
data class CategoryDraft(
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val kind: CategoryKind,
)

/** [editingId] is `null` when creating a new category. */
data class CategoryEditorState(
    val editingId: CategoryId?,
    val isBuiltIn: Boolean,
    val initial: CategoryDraft,
    val nameError: UiText? = null,
)

sealed interface DeleteDialogState {
    val category: Category

    data class Checking(
        override val category: Category,
    ) : DeleteDialogState

    data class ConfirmDelete(
        override val category: Category,
    ) : DeleteDialogState

    data class InUse(
        override val category: Category,
        val usage: CategoryUsage,
    ) : DeleteDialogState

    data class CannotVerify(
        override val category: Category,
    ) : DeleteDialogState
}

enum class MoveDirection { UP, DOWN }
