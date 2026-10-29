package com.joaoeoneves.fintrack.ui.settings.categories

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.color
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.icon
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackDropdownMenu
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackIconButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackMenuItem
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private const val ARCHIVED_ALPHA = 0.6f
private val ColorDotSize = 8.dp

/** Localized label of a [CategoryKind]. */
internal val CategoryKind.label: String
    @Composable
    get() =
        when (this) {
            CategoryKind.SPENDING -> stringResource(R.string.category_kind_spending)
            CategoryKind.SAVING -> stringResource(R.string.category_kind_saving)
        }

@Composable
internal fun ActiveCategoryRow(
    category: Category,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: CategoryRowActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { actions.onEdit(category.id) }
                .padding(vertical = Dimens.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIdentity(category)
        LabeledIconButton(
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.category_move_up),
            onClick = { actions.onMove(category.id, MoveDirection.UP) },
            enabled = canMoveUp,
        )
        LabeledIconButton(
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.category_move_down),
            onClick = { actions.onMove(category.id, MoveDirection.DOWN) },
            enabled = canMoveDown,
        )
        CategoryOverflowMenu(category, archived = false, actions = actions)
    }
}

@Composable
internal fun ArchivedCategoryRow(
    category: Category,
    actions: CategoryRowActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Dimens.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIdentity(category, modifier = Modifier.alpha(ARCHIVED_ALPHA))
        CategoryOverflowMenu(category, archived = true, actions = actions)
    }
}

/** Overflow menu of every category row (active or archived): one pattern for all row actions. */
@Composable
private fun CategoryOverflowMenu(
    category: Category,
    archived: Boolean,
    actions: CategoryRowActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.cd_category_more_actions, category.displayName)
    val close = { expanded = false }
    FinTrackIconButton(Icons.Filled.MoreVert, contentDescription = description, onClick = { expanded = true })
    FinTrackDropdownMenu(expanded = expanded, onDismissRequest = close) {
        if (!archived) {
            CategoryMenuItem(R.string.category_action_edit, close) { actions.onEdit(category.id) }
        }
        CategoryMenuItem(R.string.category_action_move_items, close) { actions.onMoveItems(category.id) }
        CategoryMenuItem(
            if (archived) R.string.category_action_unarchive else R.string.category_action_archive,
            close,
        ) { actions.onToggleArchived(category.id) }
        if (category.builtIn) {
            FinTrackMenuItem(
                text = stringResource(R.string.category_delete_builtin_hint),
                onClick = {},
                enabled = false,
            )
        } else {
            CategoryMenuItem(R.string.category_action_delete, close) { actions.onDelete(category.id) }
        }
    }
}

@Composable
private fun CategoryMenuItem(
    @StringRes label: Int,
    onClose: () -> Unit,
    onClick: () -> Unit,
) {
    FinTrackMenuItem(
        text = stringResource(label),
        onClick = {
            onClose()
            onClick()
        },
    )
}

/** Monochrome icon, name and kind of a category plus its colour dot; fills the row's remaining width. */
@Composable
private fun RowScope.CategoryIdentity(
    category: Category,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Column(modifier = Modifier.padding(start = Dimens.Space12).weight(1f)) {
            Text(text = category.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = category.kind.label,
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
            )
        }
        ColorDot(category)
    }
}

@Composable
private fun ColorDot(category: Category) {
    Box(
        modifier =
            Modifier
                .padding(horizontal = Dimens.Space8)
                .size(ColorDotSize)
                .clip(CircleShape)
                .background(category.color),
    )
}
