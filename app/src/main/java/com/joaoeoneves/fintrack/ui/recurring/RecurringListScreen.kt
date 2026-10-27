package com.joaoeoneves.fintrack.ui.recurring

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.OnResumeEffect
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.asString

private val FabClearance = 88.dp

@Composable
fun RecurringListScreen(
    onBack: () -> Unit,
    onOpenEditor: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    OnResumeEffect(onResume = viewModel::onRetry)
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }

    RecurringListContent(
        state = uiState,
        actions =
            RecurringListActions(
                onOpenEditor = onOpenEditor,
                onTogglePaused = viewModel::onTogglePaused,
                onDeleteRequested = viewModel::onDeleteRequested,
                onConfirmDelete = viewModel::onConfirmDelete,
                onDismissDelete = viewModel::onDismissDelete,
                onRetry = viewModel::onRetry,
            ),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Stateless recurring list screen, hostable with plain state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecurringListContent(
    state: RecurringListUiState,
    actions: RecurringListActions,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { actions.onOpenEditor(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.recurring_add)) },
                modifier = Modifier.testTag(TestTags.RECURRING_ADD),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (state) {
            RecurringListUiState.Loading ->
                Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            is RecurringListUiState.Error ->
                ErrorState(message = state.message.asString(), onRetry = actions.onRetry, modifier = contentModifier)

            is RecurringListUiState.Content -> {
                RecurringItems(state, actions, contentModifier)
                state.deleteTarget?.let { target ->
                    RecurringDeleteDialog(
                        name = target.name,
                        onConfirm = actions.onConfirmDelete,
                        onDismiss = actions.onDismissDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurringItems(
    state: RecurringListUiState.Content,
    actions: RecurringListActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = FabClearance)) {
        item(key = "totals") { RecurringTotals(state) }
        if (state.active.isEmpty() && state.inactive.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.recurring_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (state.active.isNotEmpty()) {
            item(key = "active-header") { SectionHeader(R.string.recurring_section_active) }
            items(state.active, key = { it.item.id }) { RecurringRowItem(it, actions) }
        }
        if (state.inactive.isNotEmpty()) {
            item(key = "inactive-header") { SectionHeader(R.string.recurring_section_inactive) }
            items(state.inactive, key = { it.item.id }) { RecurringRowItem(it, actions) }
        }
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
