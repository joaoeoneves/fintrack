package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.UiText
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.showActionSnackbar
import kotlinx.coroutines.flow.Flow

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

/** Offers "Undo" in [snackbarHostState] each time [viewModel] deletes a manual income line. */
@Composable
fun CollectDeletedIncomeLines(
    viewModel: MonthlyPlanViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.deletedLineEvent.collect { line ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result =
                snackbarHostState.showActionSnackbar(
                    resources.getString(R.string.income_line_deleted),
                    resources.getString(R.string.action_undo),
                )
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDeleteIncomeLine(line)
        }
    }
}
