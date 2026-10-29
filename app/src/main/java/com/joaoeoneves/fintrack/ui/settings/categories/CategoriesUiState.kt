package com.joaoeoneves.fintrack.ui.settings.categories

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.usecase.CategoryMovePreview
import com.joaoeoneves.fintrack.domain.usecase.CategoryUsage
import com.joaoeoneves.fintrack.ui.common.UiText

sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState

    data class Content(
        val active: List<Category>,
        val archived: List<Category>,
        val editor: CategoryEditorState?,
        val deleteDialog: DeleteDialogState?,
        val moveDialog: MoveDialogState? = null,
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

    data class InUse(
        override val category: Category,
        val usage: CategoryUsage,
    ) : DeleteDialogState

    data class CannotVerify(
        override val category: Category,
    ) : DeleteDialogState
}

/** The "move everything to another category" flow of an archived [source] category. */
sealed interface MoveDialogState {
    val source: Category

    data class Checking(
        override val source: Category,
    ) : MoveDialogState

    data class NothingToMove(
        override val source: Category,
    ) : MoveDialogState

    data class CannotVerify(
        override val source: Category,
    ) : MoveDialogState

    data class PickTarget(
        override val source: Category,
        val targets: List<Category>,
    ) : MoveDialogState

    data class Confirm(
        override val source: Category,
        val target: Category,
        val preview: CategoryMovePreview,
    ) : MoveDialogState

    data class Moving(
        override val source: Category,
        val target: Category,
    ) : MoveDialogState
}

enum class MoveDirection { UP, DOWN }
