package com.joaoeoneves.fintrack.ui.plan.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.GoalProgress
import com.joaoeoneves.fintrack.domain.model.GoalStatus
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * The "Goals" card: a progress bar and an on track / behind label per goal. Goals are optional: without any only
 * a light "Add a goal" entry shows (plus "Create from investments" when there are items to turn into goals).
 */
@Composable
fun GoalsCard(
    state: GoalsUiState,
    actions: GoalsActions,
    modifier: Modifier = Modifier,
) {
    if (!state.loaded) return
    val entryPoints: @Composable () -> Unit = {
        GhostButton(text = stringResource(R.string.plan_goals_add), onClick = actions.onAdd)
        if (state.investmentCandidates.isNotEmpty()) {
            GhostButton(
                text = stringResource(R.string.plan_goals_from_investments),
                onClick = actions.onCreateFromInvestments,
                modifier = Modifier.padding(top = Dimens.Space8),
            )
        }
    }
    if (state.goals.isEmpty()) {
        Column(modifier = modifier.padding(top = Dimens.Space24)) { entryPoints() }
        return
    }
    SettingsGroup(label = stringResource(R.string.plan_goals_title), modifier = modifier) {
        state.goals.forEach { progress ->
            GoalRow(progress = progress, onClick = { actions.onEdit(progress.goal.id) })
            Hairline()
        }
        Column(modifier = Modifier.padding(top = Dimens.Space12)) { entryPoints() }
    }
}

@Composable
private fun GoalRow(
    progress: GoalProgress,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = Dimens.Space12),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = progress.goal.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.budget_spent_of_limit,
                        formatAmountCents(progress.savedCents),
                        formatAmountCents(progress.targetCents),
                    ),
                style = Amount,
            )
        }
        ThinProgressBar(
            progress = progress.fraction,
            isOver = false,
            modifier = Modifier.padding(top = Dimens.Space8),
        )
        Text(
            text = stringResource(progress.status.labelRes()),
            style = MaterialTheme.typography.bodySmall,
            color =
                if (progress.status == GoalStatus.BEHIND) {
                    FinTrackTheme.colors.negative
                } else {
                    FinTrackTheme.colors.textSecondary
                },
            modifier = Modifier.padding(top = Dimens.Space4),
        )
    }
}

private fun GoalStatus.labelRes(): Int =
    when (this) {
        GoalStatus.ON_TRACK -> R.string.goal_status_on_track
        GoalStatus.BEHIND -> R.string.goal_status_behind
        GoalStatus.DONE -> R.string.goal_status_done
    }
