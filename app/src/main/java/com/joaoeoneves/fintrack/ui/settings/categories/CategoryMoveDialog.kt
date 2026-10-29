package com.joaoeoneves.fintrack.ui.settings.categories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.CategoryChipFlow
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog

/**
 * The single dialog of the "move items to another category" flow, switching on [state]: checking what
 * uses the category, picking the target, confirming with counts, and moving.
 */
@Composable
internal fun CategoryMoveDialog(
    state: MoveDialogState,
    actions: MoveActions,
) {
    val name = state.source.displayName
    when (state) {
        is MoveDialogState.Checking -> {
            MoveAlert(stringResource(R.string.category_move_checking_title, name), actions.onDismiss) { Spinner() }
        }

        is MoveDialogState.NothingToMove -> {
            MoveAlert(name, actions.onDismiss, confirm = R.string.action_ok to actions.onDismiss, dismissLabel = null) {
                Text(stringResource(R.string.category_move_nothing, name))
            }
        }

        is MoveDialogState.CannotVerify -> {
            MoveAlert(name, actions.onDismiss, confirm = R.string.action_ok to actions.onDismiss, dismissLabel = null) {
                Text(stringResource(R.string.category_move_cannot_verify, name))
            }
        }

        is MoveDialogState.PickTarget -> {
            MoveAlert(stringResource(R.string.category_move_pick_title, name), actions.onDismiss) {
                CategoryChipFlow(
                    options = state.targets,
                    selected = null,
                    error = null,
                    onSelected = actions.onPickTarget,
                )
            }
        }

        is MoveDialogState.Confirm -> {
            MoveAlert(
                title = stringResource(R.string.category_move_confirm_title, state.target.displayName),
                onDismiss = actions.onDismiss,
                confirm = R.string.category_action_move to actions.onConfirm,
            ) { Text(confirmMessage(state, name)) }
        }

        is MoveDialogState.Moving -> {
            MoveAlert(stringResource(R.string.category_move_moving), {}) { Spinner() }
        }
    }
}

@Composable
private fun confirmMessage(
    state: MoveDialogState.Confirm,
    sourceName: String,
): String {
    val preview = state.preview
    val lines =
        buildList {
            add(stringResource(R.string.category_move_confirm_intro, sourceName))
            if (preview.expenseCount > 0) {
                val count = preview.expenseCount
                add("• " + pluralStringResource(R.plurals.category_move_expenses, count, count))
            }
            if (preview.recurringCount > 0) {
                add(
                    "• " +
                        pluralStringResource(
                            R.plurals.category_move_recurring,
                            preview.recurringCount,
                            preview.recurringCount,
                        ),
                )
            }
        }
    return lines.joinToString("\n")
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun MoveAlert(
    title: String,
    onDismiss: () -> Unit,
    confirm: Pair<Int, () -> Unit>? = null,
    dismissLabel: Int? = R.string.action_cancel,
    body: @Composable () -> Unit,
) {
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body,
        confirmButton = {
            confirm?.let { (label, onClick) -> DialogTextButton(text = stringResource(label), onClick = onClick) }
        },
        dismissButton = {
            dismissLabel?.let { DialogTextButton(text = stringResource(it), onClick = onDismiss) }
        },
    )
}
