package ptech.joaoe.agenticusage.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ptech.joaoe.agenticusage.domain.model.TimeRange

/**
 * A horizontally scrollable row of filter chips, one per [TimeRange] value, shared between the
 * dashboard and full expense list screens.
 */
@Composable
fun TimeRangeFilterRow(
    selected: TimeRange,
    onSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelected(range) },
                label = { Text(range.label) }
            )
        }
    }
}
