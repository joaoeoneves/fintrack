@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package com.joaoeoneves.fintrack.ui.dashboard.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.common.ExpenseRow
import com.joaoeoneves.fintrack.ui.common.IncomeRow
import com.joaoeoneves.fintrack.ui.common.SwipeToDeleteRow
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.common.rememberDeletedMessage
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.showActionSnackbar
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The "To review" card's state plus its callbacks, bundled to keep the dashboard's parameter lists short. */
data class ReviewInboxModel(
    val state: ReviewInboxUiState,
    val onConfirmAll: () -> Unit,
    val onRemove: (Transaction) -> Unit,
    val onOpenExpense: (String) -> Unit,
    val onOpenIncome: (String) -> Unit,
)

/**
 * Home's "To review" card: the records recorded automatically and not yet looked at. Swiping a row removes it,
 * tapping opens it (saving marks it reviewed) and "Confirm all" marks every one of them reviewed.
 */
@Composable
fun ReviewInboxCard(
    state: ReviewInboxUiState.Visible,
    model: ReviewInboxModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().testTag(TestTags.REVIEW_INBOX)) {
        SectionTitle(title = stringResource(R.string.home_review_title))
        Text(
            text = stringResource(R.string.home_review_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
        )
        state.items.forEach { item ->
            SwipeToDeleteRow(onDelete = { model.onRemove(item) }, deleteLabel = stringResource(R.string.cd_delete)) {
                val rowModifier = Modifier.weight(1f)
                when (item) {
                    is Expense ->
                        ExpenseRow(item, rowModifier, onClick = { model.onOpenExpense(item.id) }, showDate = true)

                    is Income ->
                        IncomeRow(item, rowModifier, onClick = { model.onOpenIncome(item.id) }, showDate = true)
                }
            }
        }
        val more = state.total - state.items.size
        if (more > 0) {
            Text(
                text = stringResource(R.string.home_review_more, more),
                style = MaterialTheme.typography.bodyMedium,
                color = FinTrackTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = Dimens.Space8),
            )
        }
        GhostButton(
            text = stringResource(R.string.home_review_confirm_all),
            onClick = model.onConfirmAll,
            modifier = Modifier.padding(top = Dimens.Space8),
        )
    }
}

/** Shows the "N confirmed" and "removed / Undo" snackbars of [viewModel] in [snackbarHostState]. */
@Composable
fun ReviewInboxSnackbars(
    viewModel: ReviewInboxViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val undoLabel = stringResource(R.string.action_undo)
    val deletedMessage = rememberDeletedMessage()
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                is ReviewInboxEvent.Confirmed ->
                    snackbarHostState.showSnackbar(
                        resources.getQuantityString(R.plurals.home_review_confirmed, event.count, event.count),
                    )

                is ReviewInboxEvent.Removed ->
                    if (snackbarHostState.showActionSnackbar(deletedMessage(event.deleted), undoLabel) ==
                        SnackbarResult.ActionPerformed
                    ) {
                        viewModel.onUndoRemove(event.deleted)
                    }
            }
        }
    }
}
