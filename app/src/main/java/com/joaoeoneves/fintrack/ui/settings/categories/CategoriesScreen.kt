package com.joaoeoneves.fintrack.ui.settings.categories

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.ListSkeleton
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.StateCrossfade
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.SectionLabel
import com.joaoeoneves.fintrack.ui.theme.motionAnimateItem

/** Callbacks of a single category row. */
internal data class CategoryRowActions(
    val onEdit: (CategoryId) -> Unit,
    val onMove: (CategoryId, MoveDirection) -> Unit,
    val onToggleArchived: (CategoryId) -> Unit,
    val onDelete: (CategoryId) -> Unit,
    val onMoveItems: (CategoryId) -> Unit,
)

/** Callbacks of the "move items to…" dialogs. */
internal data class MoveActions(
    val onPickTarget: (Category) -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

/** Bundles [CategoriesContent]'s callbacks to keep its parameter list short. */
internal data class CategoriesActions(
    val rows: CategoryRowActions,
    val onAdd: () -> Unit,
    val onDismissEditor: () -> Unit,
    val onSave: (CategoryDraft) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissDelete: () -> Unit,
    val move: MoveActions,
)

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }

    CategoriesContent(
        state = uiState,
        actions =
            CategoriesActions(
                rows =
                    CategoryRowActions(
                        onEdit = viewModel::onEdit,
                        onMove = viewModel::onMove,
                        onToggleArchived = viewModel::onToggleArchived,
                        onDelete = viewModel::onDeleteRequested,
                        onMoveItems = viewModel::onMoveItemsRequested,
                    ),
                onAdd = viewModel::onAdd,
                onDismissEditor = viewModel::onDismissEditor,
                onSave = viewModel::onSaveEditor,
                onConfirmDelete = viewModel::onConfirmDelete,
                onDismissDelete = viewModel::onDismissDelete,
                move =
                    MoveActions(
                        onPickTarget = viewModel::onMoveTargetPicked,
                        onConfirm = viewModel::onConfirmMove,
                        onDismiss = viewModel::onDismissMove,
                    ),
            ),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Stateless categories screen, hostable with plain state (e.g. from an instrumented test). */
@Composable
internal fun CategoriesContent(
    state: CategoriesUiState,
    actions: CategoriesActions,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    LargeTitleScaffold(
        title = stringResource(R.string.categories_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
        bottomBar = {
            PrimaryPillButton(
                text = stringResource(R.string.categories_add),
                onClick = actions.onAdd,
                modifier =
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space12),
            )
        },
    ) { innerPadding ->
        StateCrossfade(state) { state ->
            when (state) {
                CategoriesUiState.Loading -> ListSkeleton(Modifier.padding(innerPadding))

                is CategoriesUiState.Content -> {
                    CategoryList(state, actions.rows, Modifier.padding(innerPadding))
                    state.editor?.let { editor ->
                        CategoryEditorDialog(
                            state = editor,
                            onSave = actions.onSave,
                            onDismiss = actions.onDismissEditor,
                        )
                    }
                    state.deleteDialog?.let { dialog ->
                        CategoryDeleteDialog(
                            state = dialog,
                            onDelete = actions.onConfirmDelete,
                            onArchive = { actions.rows.onToggleArchived(dialog.category.id) },
                            onDismiss = actions.onDismissDelete,
                        )
                    }
                    state.moveDialog?.let { dialog -> CategoryMoveDialog(dialog, actions.move) }
                }
            }
        }
    }
}

@Composable
private fun CategoryList(
    state: CategoriesUiState.Content,
    actions: CategoryRowActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding),
    ) {
        itemsIndexed(state.active, key = { _, category -> category.id.value }) { index, category ->
            ActiveCategoryRow(
                category = category,
                canMoveUp = index > 0,
                canMoveDown = index < state.active.lastIndex,
                actions = actions,
                modifier = motionAnimateItem(),
            )
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived-header") {
                Text(
                    text = stringResource(R.string.categories_archived_header),
                    style = SectionLabel,
                    color = FinTrackTheme.colors.textSecondary,
                    modifier =
                        Modifier
                            .padding(top = Dimens.Space24, bottom = Dimens.Space8)
                            .semantics { heading() },
                )
            }
            itemsIndexed(state.archived, key = { _, category -> category.id.value }) { _, category ->
                ArchivedCategoryRow(category = category, actions = actions, modifier = motionAnimateItem())
            }
        }
    }
}
