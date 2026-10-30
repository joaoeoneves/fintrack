@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package com.joaoeoneves.fintrack.ui.dashboard.recurring

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.RecurringCandidate
import com.joaoeoneves.fintrack.ui.designsystem.showActionSnackbar

/** The "Make it recurring?" suggestion's state plus its callbacks, bundled to keep the dashboard's parameter lists short. */
data class RecurringSuggestionModel(
    val state: RecurringSuggestionUiState,
    val onAccept: (RecurringCandidate) -> Unit,
    val onDismiss: (RecurringCandidate) -> Unit,
)

/** Offers "Undo" in a snackbar each time a suggestion is dismissed. */
@Composable
fun RecurringSuggestionSnackbars(
    viewModel: RecurringSuggestionViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val message = stringResource(R.string.home_recurring_suggestion_dismissed)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(viewModel) {
        viewModel.dismissedEvent.collect { candidate ->
            snackbarHostState.currentSnackbarData?.dismiss()
            if (snackbarHostState.showActionSnackbar(message, undoLabel) == SnackbarResult.ActionPerformed) {
                viewModel.onUndoDismiss(candidate)
            }
        }
    }
}
