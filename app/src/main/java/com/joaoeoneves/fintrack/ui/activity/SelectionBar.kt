package com.joaoeoneves.fintrack.ui.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.active
import com.joaoeoneves.fintrack.ui.common.CategoryChipFlow
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackIconButton
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val BarShadow = 8.dp

/**
 * The multi-selection bar that takes the place of the add pill: a close button, the "N selected" count and the
 * Category / Meal card (when it applies) / Delete actions. Category opens a sheet to pick the new one.
 */
@Composable
fun SelectionBar(
    selection: SelectionState,
    categories: List<Category>,
    actions: SelectionBarActions,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = FinTrackTheme.colors.surface2,
        border = BorderStroke(Dimens.HairlineThickness, FinTrackTheme.colors.hairline),
        shadowElevation = BarShadow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.Space8),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FinTrackIconButton(
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.selection_cancel),
                onClick = actions.onClear,
            )
            Text(
                text = pluralStringResource(R.plurals.selection_count, selection.count, selection.count),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = Dimens.Space8),
            )
            BarAction(stringResource(R.string.selection_category)) { picking = true }
            if (selection.showMealCard) BarAction(stringResource(R.string.selection_meal_card), actions.onMealCard)
            BarAction(stringResource(R.string.selection_delete), actions.onDelete)
        }
    }
    if (picking) {
        FinTrackBottomSheet(onDismissRequest = { picking = false }) {
            CategoryChipFlow(
                options = categories.active(),
                selected = null,
                error = null,
                onSelected = {
                    picking = false
                    actions.onCategory(it)
                },
                modifier = Modifier.padding(bottom = Dimens.Space16),
            )
        }
    }
}

@Composable
private fun BarAction(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier =
            Modifier
                .heightIn(min = Dimens.AddPillHeight)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Dimens.Space12)
                .padding(vertical = Dimens.Space12),
    )
}
