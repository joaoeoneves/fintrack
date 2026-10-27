package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.ui.settings.categories.MoveDirection

/** What the manage-groups sheet can trigger, bundled for detekt's LongParameterList. */
data class ManageGroupsActions(
    val onAdd: (String) -> Unit,
    val onRename: (String, String) -> Unit,
    val onMove: (String, MoveDirection) -> Unit,
    val onDelete: (String) -> Unit,
)
