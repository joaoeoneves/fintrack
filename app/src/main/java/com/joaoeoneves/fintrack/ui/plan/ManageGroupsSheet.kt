package com.joaoeoneves.fintrack.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.isGroupNameTaken
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.settings.categories.MoveDirection
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The sheet to add, rename, reorder and delete category groups. Deleting asks first. */
@Composable
fun ManageGroupsSheet(
    groups: List<CategoryGroup>,
    actions: ManageGroupsActions,
    onDismiss: () -> Unit,
    reservedNames: List<String> = emptyList(),
) {
    var editing by remember { mutableStateOf<CategoryGroup?>(null) }
    var deleting by remember { mutableStateOf<CategoryGroup?>(null) }

    FinTrackBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.groups_manage_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimens.Space12),
        )
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.groups_none_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
            groups.forEachIndexed { index, group ->
                GroupRow(
                    group = group,
                    canMoveUp = index > 0,
                    canMoveDown = index < groups.lastIndex,
                    actions = actions,
                    rowActions = GroupRowActions(onRename = { editing = group }, onDelete = { deleting = group }),
                )
                Hairline()
            }
        }
        NameEditor(
            editing = editing,
            nameCheck = { name -> isGroupNameTaken(name, groups, excludingId = editing?.id, reserved = reservedNames) },
            onSubmit = { name ->
                editing?.let { actions.onRename(it.id, name) } ?: actions.onAdd(name)
                editing = null
            },
            onCancelRename = { editing = null },
        )
    }
    deleting?.let { group ->
        DeleteGroupDialog(
            name = group.name,
            onConfirm = {
                actions.onDelete(group.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun GroupRow(
    group: CategoryGroup,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: ManageGroupsActions,
    rowActions: GroupRowActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        LabeledIconButton(
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.category_move_up),
            onClick = { actions.onMove(group.id, MoveDirection.UP) },
            enabled = canMoveUp,
        )
        LabeledIconButton(
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.category_move_down),
            onClick = { actions.onMove(group.id, MoveDirection.DOWN) },
            enabled = canMoveDown,
        )
        LabeledIconButton(
            icon = Icons.Filled.Edit,
            contentDescription = stringResource(R.string.groups_rename),
            onClick = rowActions.onRename,
        )
        LabeledIconButton(
            icon = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.category_action_delete),
            onClick = rowActions.onDelete,
        )
    }
}

/** The name field under the list: adds a group, or renames the one picked with the edit button. */
@Composable
private fun NameEditor(
    editing: CategoryGroup?,
    nameCheck: (String) -> Boolean,
    onSubmit: (String) -> Unit,
    onCancelRename: () -> Unit,
) {
    var text by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    val taken = nameCheck(text)
    val canSubmit = text.isNotBlank() && !taken
    Column(
        modifier = Modifier.padding(top = Dimens.Space16),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        MinimalTextField(
            value = text,
            onValueChange = { text = limitInput(text, it, MAX_GROUP_NAME_LENGTH) },
            label = stringResource(R.string.groups_field_name),
            isError = taken,
            supportingText = {
                FieldSupportingText(
                    error = if (taken) stringResource(R.string.groups_name_taken) else null,
                    length = text.codePointLength(),
                    max = MAX_GROUP_NAME_LENGTH,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (editing != null) {
            PrimaryPillButton(
                text = stringResource(R.string.action_save),
                onClick = { onSubmit(text) },
                enabled = canSubmit,
            )
            TextButton(onClick = onCancelRename, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.action_cancel))
            }
        } else {
            GhostButton(
                text = stringResource(R.string.groups_add),
                onClick = {
                    onSubmit(text)
                    text = ""
                },
                enabled = canSubmit,
            )
        }
    }
}

@Composable
private fun DeleteGroupDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_delete_title, name)) },
        text = { Text(stringResource(R.string.groups_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.category_action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private class GroupRowActions(
    val onRename: () -> Unit,
    val onDelete: () -> Unit,
)
