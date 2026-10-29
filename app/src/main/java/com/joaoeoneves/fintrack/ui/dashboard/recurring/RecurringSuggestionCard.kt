@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package com.joaoeoneves.fintrack.ui.dashboard.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.RecurringCandidate
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.showActionSnackbar
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The "Make it recurring?" card's state plus its callbacks, bundled to keep the dashboard's parameter lists short. */
data class RecurringSuggestionModel(
    val state: RecurringSuggestionUiState,
    val onAccept: (RecurringCandidate) -> Unit,
    val onDismiss: (RecurringCandidate) -> Unit,
)

/** Home's "Make it recurring?" card: an expense seen in each of the last 3 months, with accept and dismiss. */
@Composable
fun RecurringSuggestionCard(
    candidate: RecurringCandidate,
    model: RecurringSuggestionModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().testTag(TestTags.RECURRING_SUGGESTION)) {
        SectionTitle(title = stringResource(R.string.home_recurring_suggestion_title))
        Text(
            text =
                stringResource(
                    R.string.home_recurring_suggestion_body,
                    candidate.name,
                    formatAmountCents(candidate.amountCents),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = FinTrackTheme.colors.textSecondary,
        )
        Row(
            modifier = Modifier.padding(top = Dimens.Space12),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            GhostButton(
                text = stringResource(R.string.home_recurring_suggestion_accept),
                onClick = { model.onAccept(candidate) },
            )
            GhostButton(
                text = stringResource(R.string.home_recurring_suggestion_dismiss),
                onClick = { model.onDismiss(candidate) },
            )
        }
    }
}

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
