package com.joaoeoneves.fintrack.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.core.designsystem.R
import com.joaoeoneves.fintrack.ui.theme.Dimens

/**
 * Whether [value] differs from the first non-null value it had: the editor's "unsaved changes" check. `false`
 * while [value] is still `null` (loading). The baseline is kept while this composable stays in the composition.
 */
@Composable
fun <T> rememberIsDirty(value: T?): Boolean {
    var baseline by remember { mutableStateOf<T?>(null) }
    if (baseline == null && value != null) baseline = value
    return baseline != null && value != baseline
}

/**
 * Guards leaving an editor with unsaved changes. Returns the function every way out (back, X, system back) must
 * call: it runs [onLeave] straight away when not [dirty], otherwise asks "Descartar alterações?" first. System back
 * is intercepted while dirty.
 */
@Composable
fun rememberLeaveGuard(
    dirty: Boolean,
    onLeave: () -> Unit,
): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { asking = true }
    if (asking) {
        FinTrackAlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                DestructiveTextButton(
                    text = stringResource(R.string.discard_changes_confirm),
                    onClick = {
                        asking = false
                        onLeave()
                    },
                )
            },
            dismissButton = {
                DialogTextButton(text = stringResource(R.string.discard_changes_keep), onClick = { asking = false })
            },
        )
    }
    return { if (dirty) asking = true else onLeave() }
}

/**
 * The one editor layout: a large title with back, [content], and a sticky "Guardar" pinned above the keyboard.
 * Leaving while [dirty] asks to discard the changes first (see [rememberLeaveGuard]).
 */
@Suppress("LongParameterList") // title, leave guard, save and slots are independent
@Composable
fun EditorScaffold(
    title: String,
    dirty: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
    saveEnabled: Boolean = true,
    showSave: Boolean = true,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val requestLeave = rememberLeaveGuard(dirty, onBack)
    LargeTitleScaffold(
        title = title,
        onBack = requestLeave,
        modifier = modifier,
        snackbarHost = snackbarHost,
        bottomBar = {
            if (showSave) {
                PrimaryPillButton(
                    text = stringResource(R.string.editor_save),
                    onClick = onSave,
                    enabled = saveEnabled,
                    loading = saving,
                    modifier =
                        Modifier
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space12),
                )
            }
        },
        content = content,
    )
}
