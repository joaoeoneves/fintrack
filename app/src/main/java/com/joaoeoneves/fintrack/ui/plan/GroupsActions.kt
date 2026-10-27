package com.joaoeoneves.fintrack.ui.plan

/** What the groups card can trigger, bundled for detekt's LongParameterList. */
data class GroupsActions(
    val onCreateSuggested: () -> Unit,
    val onManage: () -> Unit,
)
