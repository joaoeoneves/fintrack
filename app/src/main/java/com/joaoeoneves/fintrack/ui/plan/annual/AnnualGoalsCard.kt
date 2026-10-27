package com.joaoeoneves.fintrack.ui.plan.annual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AnnualGoal
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.ThinProgressBar
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens

/** The goals with what their linked recurring items plan to put aside in the year, against each target. */
@Composable
fun AnnualGoalsCard(
    goals: List<AnnualGoal>,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.annual_goals_title), modifier = modifier) {
        goals.forEachIndexed { index, goal ->
            if (index > 0) Hairline()
            AnnualGoalRow(goal)
        }
    }
}

@Composable
private fun AnnualGoalRow(row: AnnualGoal) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space12)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = Dimens.SettingsRowMinHeight),
        ) {
            Text(
                text = row.goal.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.annual_goal_planned_of_target,
                        formatAmountCents(row.plannedCents),
                        formatAmountCents(row.goal.targetCents),
                    ),
                style = Amount,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        ThinProgressBar(progress = row.fraction(), isOver = false, modifier = Modifier.padding(top = Dimens.Space8))
    }
}

private fun AnnualGoal.fraction(): Float {
    if (goal.targetCents <= 0L) return 0f
    return (plannedCents.toDouble() / goal.targetCents.toDouble()).toFloat().coerceIn(0f, 1f)
}
