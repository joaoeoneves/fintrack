package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackCheckboxRow
import com.joaoeoneves.fintrack.ui.theme.Dimens

/**
 * Asks whether the user's own monthly [lines] should be removed now that the salary profile covers salary and
 * meal allowance. All are ticked at first; [onRemove] gets the ticked ids.
 */
@Composable
internal fun SalaryDuplicatesDialog(
    lines: List<PlannedIncome>,
    onRemove: (Set<String>) -> Unit,
    onKeep: () -> Unit,
) {
    var selected by remember(lines) { mutableStateOf(lines.map { it.id }.toSet()) }
    FinTrackAlertDialog(
        onDismissRequest = onKeep,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
                Text(stringResource(R.string.salary_duplicates_message))
                lines.forEach { line ->
                    val checked = line.id in selected
                    FinTrackCheckboxRow(
                        checked = checked,
                        onCheckedChange = { selected = if (checked) selected - line.id else selected + line.id },
                        label = line.label,
                        trailing = formatAmountCents(line.amountCents),
                    )
                }
            }
        },
        confirmButton = {
            DestructiveTextButton(
                text = stringResource(R.string.salary_duplicates_remove),
                onClick = { onRemove(selected) },
            )
        },
        dismissButton = { DialogTextButton(text = stringResource(R.string.salary_duplicates_keep), onClick = onKeep) },
    )
}
