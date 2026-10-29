package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChip
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChipIcon
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FieldErrorText
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackIconButton
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.rememberLeaveGuard
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * Shell of the amount-first add/edit screens: a small centred [title] with a close icon
 * ("Cancel"), and a pill "Save" bar pinned above the keyboard (shown only when [showSave]). Leaving while
 * [dirty] asks to discard the unsaved changes first.
 */
@Suppress("LongParameterList") // scaffold slots shared by three screens
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScaffold(
    title: String,
    onCancel: () -> Unit,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    showSave: Boolean = true,
    dirty: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val requestLeave = rememberLeaveGuard(dirty, onCancel)
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    FinTrackIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_cancel),
                        onClick = requestLeave,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
        bottomBar = { if (showSave) FormSaveBar(enabled = saveEnabled, onSave = onSave) },
        content = content,
    )
}

@Composable
private fun FormSaveBar(
    enabled: Boolean,
    onSave: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    PrimaryPillButton(
        text = stringResource(R.string.action_save),
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onSave()
        },
        enabled = enabled,
        modifier =
            Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space12),
    )
}

/**
 * The "Category" label (plain text) over a wrapping row of single-select [CategoryChip]s, all
 * visible at once, with an optional [error] underneath. [selected] may be null (nothing chosen).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChipFlow(
    options: List<Category>,
    selected: Category?,
    error: String?,
    onSelected: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
        Text(
            text = stringResource(R.string.expense_field_category),
            style = MaterialTheme.typography.labelLarge,
            color = FinTrackTheme.colors.textSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            options.forEach { category ->
                CategoryChip(
                    label = category.displayName,
                    selected = category.id == selected?.id,
                    onClick = { onSelected(category) },
                    leading = { tint -> CategoryChipIcon(category.icon, tint) },
                )
            }
        }
        if (error != null) {
            FieldErrorText(text = error)
        }
    }
}

/** Inline save-failure message shown at the bottom of a form body. */
@Composable
fun FormSaveError(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        color = FinTrackTheme.colors.negative,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}

/** The centred "Delete" text button at the end of an edit form. */
@Composable
fun ColumnScope.FormDeleteButton(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    DestructiveTextButton(
        text = stringResource(R.string.action_delete),
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}
