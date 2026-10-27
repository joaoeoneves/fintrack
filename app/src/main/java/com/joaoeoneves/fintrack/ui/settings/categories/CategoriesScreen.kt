package com.joaoeoneves.fintrack.ui.settings.categories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.ui.common.asString

/** Callbacks of a single category row. */
internal data class CategoryRowActions(
    val onEdit: (CategoryId) -> Unit,
    val onMove: (CategoryId, MoveDirection) -> Unit,
    val onToggleArchived: (CategoryId) -> Unit,
    val onDelete: (CategoryId) -> Unit,
)

/** Bundles [CategoriesContent]'s callbacks to keep its parameter list short. */
internal data class CategoriesActions(
    val rows: CategoryRowActions,
    val onAdd: () -> Unit,
    val onDismissEditor: () -> Unit,
    val onSave: (CategoryDraft) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissDelete: () -> Unit,
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
                    ),
                onAdd = viewModel::onAdd,
                onDismissEditor = viewModel::onDismissEditor,
                onSave = viewModel::onSaveEditor,
                onConfirmDelete = viewModel::onConfirmDelete,
                onDismissDelete = viewModel::onDismissDelete,
            ),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

private val FabClearance = 88.dp

/** Stateless categories screen, hostable with plain state (e.g. from an instrumented test). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesContent(
    state: CategoriesUiState,
    actions: CategoriesActions,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.categories_add)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (state) {
            CategoriesUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is CategoriesUiState.Content -> {
                CategoryList(state, actions.rows, Modifier.padding(innerPadding))
                state.editor?.let { editor ->
                    CategoryEditorDialog(editor, onSave = actions.onSave, onDismiss = actions.onDismissEditor)
                }
                state.deleteDialog?.let { dialog ->
                    CategoryDeleteDialog(
                        state = dialog,
                        onDelete = actions.onConfirmDelete,
                        onArchive = { actions.rows.onToggleArchived(dialog.category.id) },
                        onDismiss = actions.onDismissDelete,
                    )
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
        contentPadding = PaddingValues(bottom = FabClearance),
    ) {
        itemsIndexed(state.active, key = { _, category -> category.id.value }) { index, category ->
            ActiveCategoryRow(
                category = category,
                canMoveUp = index > 0,
                canMoveDown = index < state.active.lastIndex,
                actions = actions,
            )
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived-header") {
                Text(
                    text = stringResource(R.string.categories_archived_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            itemsIndexed(state.archived, key = { _, category -> category.id.value }) { _, category ->
                ArchivedCategoryRow(category = category, actions = actions)
            }
        }
    }
}
