package com.joaoeoneves.fintrack.ui.expense.list

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.ui.common.displayName

/**
 * Horizontally scrollable, single-select category filter: an "All" chip (selected when [selected]
 * is null) followed by one chip per entry of [categories] (in order).
 */
@Composable
fun CategoryFilterRow(
    categories: List<Category>,
    selected: CategoryId?,
    onSelected: (CategoryId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text(stringResource(R.string.filter_all)) },
        )
        categories.forEach { category ->
            FilterChip(
                selected = selected == category.id,
                onClick = { onSelected(category.id) },
                label = { Text(category.displayName) },
            )
        }
    }
}
