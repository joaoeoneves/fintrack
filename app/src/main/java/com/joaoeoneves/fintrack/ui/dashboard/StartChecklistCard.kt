package com.joaoeoneves.fintrack.ui.dashboard

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.StartStep
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SectionAction
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
import com.joaoeoneves.fintrack.ui.designsystem.showActionSnackbar
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** Home's "Get started" checklist: a dismissable list of set-up steps; tapping an open step opens its screen. */
@Composable
fun StartChecklistCard(
    state: StartChecklistUiState.Visible,
    onOpenStep: (StartStep) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().testTag(TestTags.START_CHECKLIST)) {
        SectionTitle(
            title = stringResource(R.string.home_start_title),
            trailing = {
                SectionAction(text = stringResource(R.string.home_start_dismiss), onClick = onDismiss)
            },
        )
        Text(
            text = stringResource(R.string.home_start_progress, state.doneCount, state.total),
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
        )
        ThinProgressBar(
            progress = state.doneCount.toFloat() / state.total,
            modifier = Modifier.padding(vertical = Dimens.Space8),
        )
        val paydayHint = stringResource(R.string.home_start_step_payday_hint)
        state.steps.forEach { step ->
            val done = step.done
            SettingsRow(
                title = stringResource(step.step.titleRes()),
                supportingText = if (step.step == StartStep.PAYDAY) paydayHint else null,
                value = if (done) stringResource(R.string.home_start_done) else null,
                onClick = if (done) null else ({ onOpenStep(step.step) }),
                showChevron = !done,
                modifier = Modifier.testTag(TestTags.startStep(step.step.name)),
            )
            Hairline()
        }
    }
}

private fun StartStep.titleRes(): Int =
    when (this) {
        StartStep.SALARY -> R.string.home_start_step_salary
        StartStep.BILLS -> R.string.home_start_step_bills
        StartStep.PAYDAY -> R.string.home_start_step_payday
        StartStep.FIRST_EXPENSE -> R.string.home_start_step_expense
    }

/** Offers "Undo" in a snackbar each time the "Get started" card is dismissed. */
@Composable
fun StartDismissedSnackbar(
    viewModel: StartChecklistViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val message = stringResource(R.string.home_start_dismissed)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(Unit) {
        viewModel.dismissedEvent.collect {
            snackbarHostState.currentSnackbarData?.dismiss()
            if (snackbarHostState.showActionSnackbar(message, undoLabel) == SnackbarResult.ActionPerformed) {
                viewModel.onUndoDismiss()
            }
        }
    }
}
