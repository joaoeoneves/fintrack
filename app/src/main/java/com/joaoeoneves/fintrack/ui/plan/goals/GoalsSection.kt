package com.joaoeoneves.fintrack.ui.plan.goals

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.asString
import kotlinx.coroutines.flow.Flow

/** Which goal the editor sheet is open for: `goalId == null` is a new goal. */
private data class GoalEditorTarget(
    val goalId: String?,
)

/** The goals card on the Plan tab's month view, with its editor sheet. */
@Composable
fun GoalsSection(
    modifier: Modifier = Modifier,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GoalEditorTarget?>(null) }
    GoalsCard(
        state = state,
        actions =
            GoalsActions(
                onAdd = { editing = GoalEditorTarget(null) },
                onEdit = { editing = GoalEditorTarget(it) },
                onCreateFromInvestments = viewModel::onCreateFromInvestments,
            ),
        modifier = modifier,
    )
    editing?.let { target ->
        GoalEditorSheet(
            goal = state.goals.firstOrNull { it.goal.id == target.goalId }?.goal,
            options = state.options,
            onSave = {
                viewModel.onSave(it)
                editing = null
            },
            onDelete = {
                viewModel.onDelete(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Shows the messages of [events] in [snackbarHostState]. */
@Composable
fun CollectPlanErrors(
    snackbarHostState: SnackbarHostState,
    events: List<Flow<UiText>>,
) {
    val resources = LocalResources.current
    events.forEach { flow ->
        LaunchedEffect(flow) { flow.collect { snackbarHostState.showSnackbar(it.asString(resources)) } }
    }
}
