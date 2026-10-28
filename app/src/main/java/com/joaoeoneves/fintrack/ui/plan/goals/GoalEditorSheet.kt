package com.joaoeoneves.fintrack.ui.plan.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.ui.common.ConfirmRemoveDialog
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.theme.Dimens
import java.time.Clock
import java.time.LocalDate

/**
 * Bottom sheet to add ([goal] `null`) or edit a savings goal: name, target, ANNUAL or TOTAL, the dates and initial
 * amount of a TOTAL one, the recurring items and the category whose expenses count towards it.
 */
@Composable
fun GoalEditorSheet(
    goal: SavingsGoal?,
    options: GoalEditorOptions,
    onSave: (SavingsGoal) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now(Clock.systemDefaultZone()) }
    var draft by remember { mutableStateOf(draftOf(goal, today)) }
    var confirmingRemove by remember { mutableStateOf(false) }
    FinTrackBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(if (goal == null) R.string.goal_new else R.string.goal_edit),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimens.Space12),
        )
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            GoalBasicFields(draft = draft, onChange = { draft = it })
            if (draft.type == GoalTargetType.TOTAL) GoalTotalFields(draft = draft, onChange = { draft = it })
            GoalLinkFields(draft = draft, options = options, onChange = { draft = it })
        }
        PrimaryPillButton(
            text = stringResource(R.string.action_save),
            onClick = {
                if (draft.valid) onSave(draft.toGoal(goal)) else draft = draft.copy(showErrors = true)
            },
            modifier = Modifier.padding(top = Dimens.Space16),
        )
        if (goal != null) {
            DestructiveTextButton(
                text = stringResource(R.string.action_remove),
                onClick = { confirmingRemove = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
    if (confirmingRemove && goal != null) {
        ConfirmRemoveDialog(
            title = stringResource(R.string.confirm_remove_title, goal.name),
            message = stringResource(R.string.goal_remove_message),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                confirmingRemove = false
                onDelete(goal.id)
            },
            onDismiss = { confirmingRemove = false },
        )
    }
}
