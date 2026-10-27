package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.model.Earmark
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChip
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** What an income line can be earmarked for: the defined [groups] and the selectable [categories]. */
data class EarmarkOptions(
    val groups: List<CategoryGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
)

/** The "Earmark for" picker: None, a group, or a category; [onSelect] gets `null` for None. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EarmarkPicker(
    options: EarmarkOptions,
    selected: Earmark?,
    onSelect: (Earmark?) -> Unit,
) {
    Text(
        text = stringResource(R.string.plan_earmark_for),
        style = MaterialTheme.typography.labelLarge,
        color = FinTrackTheme.colors.textSecondary,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        CategoryChip(
            label = stringResource(R.string.plan_earmark_none),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        options.groups.forEach { group ->
            val target = Earmark.Group(group.id)
            CategoryChip(label = group.name, selected = selected == target, onClick = { onSelect(target) })
        }
        options.categories.forEach { category ->
            val target = Earmark.Category(category.id.value)
            CategoryChip(
                label = category.displayName,
                selected = selected == target,
                onClick = { onSelect(target) },
            )
        }
    }
}
