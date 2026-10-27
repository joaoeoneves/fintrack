package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R

/**
 * The donut card: actual outflow per category, with an Actual/Planned toggle (shown only when
 * the plan has planned allocations) to see the planned split instead.
 */
@Composable
fun SpendingBreakdownCard(
    state: DashboardUiState.Content,
    modifier: Modifier = Modifier,
) {
    val hasPlanned = state.plannedSlices.isNotEmpty()
    var showPlanned by rememberSaveable { mutableStateOf(false) }
    if (state.expenses.isEmpty() && !hasPlanned) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.dashboard_no_expenses),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            if (hasPlanned) {
                BreakdownToggle(
                    showPlanned = showPlanned,
                    onChange = { showPlanned = it },
                )
            }
            BreakdownBody(state = state, showPlanned = showPlanned && hasPlanned)
        }
    }
}

@Composable
private fun BreakdownBody(
    state: DashboardUiState.Content,
    showPlanned: Boolean,
) {
    val chartModifier =
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    when {
        showPlanned ->
            PieChart(
                slices = state.plannedSlices,
                modifier = chartModifier,
                centerLabel = stringResource(R.string.dashboard_planned_per_month),
                totalDescriptionRes = R.string.cd_pie_chart_planned_total,
            )

        state.expenses.isEmpty() ->
            Text(
                text = stringResource(R.string.dashboard_no_expenses),
                style = MaterialTheme.typography.bodyLarge,
                modifier = chartModifier,
            )

        else -> PieChart(slices = state.categoryTotals, modifier = chartModifier)
    }
}

@Composable
private fun BreakdownToggle(
    showPlanned: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val labels = listOf(R.string.dashboard_breakdown_actual, R.string.dashboard_breakdown_planned)
    SingleChoiceSegmentedButtonRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = (index == 1) == showPlanned,
                onClick = { onChange(index == 1) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
}
