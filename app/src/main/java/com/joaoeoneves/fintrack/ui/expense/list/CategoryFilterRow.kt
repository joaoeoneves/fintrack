package com.joaoeoneves.fintrack.ui.expense.list

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChip
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val DividerHeight = 24.dp

/** Test tag of the "All" chip; the per-category chips use `filter_chip_<category id>`. */
const val FILTER_CHIP_ALL_TAG = "filter_chip_all"

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
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Dimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = Dimens.Space12)
                        .width(Dimens.HairlineThickness)
                        .height(DividerHeight)
                        .background(FinTrackTheme.colors.hairline),
            )
        }
        FilterChips(categories, selected, onSelected, Modifier.weight(1f))
    }
}

@Composable
private fun FilterChips(
    categories: List<Category>,
    selected: CategoryId?,
    onSelected: (CategoryId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .horizontalScroll(rememberScrollState())
                .padding(end = Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        CategoryChip(
            label = stringResource(R.string.filter_all),
            selected = selected == null,
            onClick = { onSelected(null) },
            modifier = Modifier.testTag(FILTER_CHIP_ALL_TAG),
        )
        categories.forEach { category ->
            CategoryChip(
                label = category.displayName,
                selected = selected == category.id,
                onClick = { onSelected(category.id) },
                modifier = Modifier.testTag("filter_chip_${category.id.value}"),
            )
        }
    }
}
