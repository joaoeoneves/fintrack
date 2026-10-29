package com.joaoeoneves.fintrack.ui.activity.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChip
import com.joaoeoneves.fintrack.ui.theme.Dimens

/** Test tag of the "All" source chip; the per-source chips use `income_source_chip_<key>`. */
const val INCOME_SOURCE_CHIP_ALL_TAG = "income_source_chip_all"

/** Wrapping, single-select filter of the Income tab: an "All" chip followed by one chip per source. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncomeSourceFilterRow(
    sources: List<IncomeSourceChip>,
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val other = stringResource(R.string.activity_source_other)
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        CategoryChip(
            label = stringResource(R.string.activity_source_all),
            selected = selected == null,
            onClick = { onSelected(null) },
            modifier = Modifier.testTag(INCOME_SOURCE_CHIP_ALL_TAG),
        )
        sources.forEach { source ->
            CategoryChip(
                label = source.label ?: other,
                selected = selected == source.key,
                onClick = { onSelected(source.key) },
                modifier = Modifier.testTag("income_source_chip_${source.key}"),
            )
        }
    }
}
