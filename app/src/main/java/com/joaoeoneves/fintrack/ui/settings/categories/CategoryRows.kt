package com.joaoeoneves.fintrack.ui.settings.categories

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.color
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.icon

private const val ARCHIVED_ALPHA = 0.6f

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
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
        CategoryOverflowMenu(category, actions)
    }
}

@Composable
private fun CategoryOverflowMenu(
    category: Category,
    actions: CategoryRowActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val name = category.displayName
    val description = stringResource(R.string.cd_category_more_actions, name)
    IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(Icons.Filled.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.category_action_edit)) },
            onClick = {
                expanded = false
                actions.onEdit(category.id)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.category_action_archive)) },
            onClick = {
                expanded = false
                actions.onToggleArchived(category.id)
            },
        )
        if (!category.builtIn) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.category_action_delete)) },
                onClick = {
                    expanded = false
                    actions.onDelete(category.id)
                },
            )
        }
    }
}

@Composable
internal fun ArchivedCategoryRow(
    category: Category,
    actions: CategoryRowActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIdentity(category, modifier = Modifier.alpha(ARCHIVED_ALPHA))
        TextButton(onClick = { actions.onToggleArchived(category.id) }) {
            Text(stringResource(R.string.category_action_unarchive))
        }
        if (!category.builtIn) {
            TextButton(onClick = { actions.onDelete(category.id) }) {
                Text(stringResource(R.string.category_action_delete))
            }
        }
    }
}

/** Icon, color dot, name and kind of a category; fills the row's remaining width. */
@Composable
private fun RowScope.CategoryIdentity(
    category: Category,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Icon(category.icon, contentDescription = null, tint = category.color)
        ColorDot(category)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = category.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = category.kind.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColorDot(category: Category) {
    Box(
        modifier =
            Modifier
                .padding(start = 8.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(category.color),
    )
}
