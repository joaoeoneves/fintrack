package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryBudget
import com.joaoeoneves.fintrack.ui.common.ErrorState
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.dashboard.EditBudgetDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyPlanScreen(
    onBack: () -> Unit,
    viewModel: MonthlyPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (val state = uiState) {
            MonthlyPlanUiState.Loading ->
                Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            is MonthlyPlanUiState.Error ->
                ErrorState(message = state.message.asString(), onRetry = viewModel::onRetry, modifier = contentModifier)

            is MonthlyPlanUiState.Content -> MonthlyPlanContent(state, viewModel, contentModifier)
        }
    }
}

@Composable
private fun MonthlyPlanContent(
    state: MonthlyPlanUiState.Content,
    viewModel: MonthlyPlanViewModel,
    modifier: Modifier = Modifier,
) {
    var editingAllocation by remember { mutableStateOf<CategoryBudget?>(null) }
    val cardModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        item { PlanSummaryCard(summary = state.summary, modifier = cardModifier) }
        item {
            ExpectedIncomeCard(
                lines = state.incomeLines,
                canAdd = state.canAddIncomeLine,
                onAdd = viewModel::onAddIncomeLine,
                onEdit = viewModel::onEditIncomeLine,
                modifier = cardModifier,
            )
        }
        item {
            AllocationsCard(
                allocations = state.allocations,
                onEdit = { editingAllocation = it },
                modifier = cardModifier,
            )
        }
    }

    state.editor?.let { editor ->
        IncomeLineDialog(
            editor = editor,
            onSave = viewModel::onSaveIncomeLine,
            onDelete = viewModel::onDeleteIncomeLine,
            onDismiss = viewModel::onDismissEditor,
        )
    }
    editingAllocation?.let { budget ->
        EditBudgetDialog(
            budget = budget,
            onSave = {
                viewModel.onSetAllocation(budget.category, it)
                editingAllocation = null
            },
            onRemove = {
                viewModel.onClearAllocation(budget.category)
                editingAllocation = null
            },
            onDismiss = { editingAllocation = null },
        )
    }
}
