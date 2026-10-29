package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** How a [PendingOccurrenceRow]'s status line is coloured. */
enum class PendingTone { NEUTRAL, ACCENT, NEGATIVE }

/** The labels of a [PendingOccurrenceRow]'s controls (parameters so the design system owns no copy). */
data class PendingLabels(
    val confirm: String,
    val more: String,
    val skip: String,
    val changeAmount: String,
)

/**
 * One occurrence waiting to be confirmed (a recurring bill) or received (expected income): [title] and [amountText],
 * a [statusText] line (due date, "overdue", "Automatic - dd/MM") and an optional [extraText] line.
 *
 * The check button runs [onConfirm]; the overflow menu offers [onSkip] ("Ignorar") and [onChangeAmount] ("Alterar
 * valor"), each shown only when non-null. With [onConfirm] `null` the row has no controls at all (a future
 * auto-posted row). [confirmEnabled] disables the check (not due yet).
 */
@Suppress("LongParameterList", "LongMethod") // one row: text lines plus its three actions
@Composable
fun PendingOccurrenceRow(
    title: String,
    amountText: String,
    statusText: String,
    labels: PendingLabels,
    modifier: Modifier = Modifier,
    statusTone: PendingTone = PendingTone.NEUTRAL,
    extraText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    onSkip: (() -> Unit)? = null,
    onChangeAmount: (() -> Unit)? = null,
) {
    val colors = FinTrackTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Dimens.Space4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = Dimens.Space8)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color =
                    when (statusTone) {
                        PendingTone.NEUTRAL -> colors.textSecondary
                        PendingTone.ACCENT -> colors.accentText
                        PendingTone.NEGATIVE -> colors.negative
                    },
            )
            if (extraText != null) {
                Text(text = extraText, style = MaterialTheme.typography.bodySmall, color = colors.negative)
            }
        }
        Text(text = amountText, style = Amount, maxLines = 1)
        if (onConfirm != null) {
            IconButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.testTag(TestTags.RECURRING_CONFIRM),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = labels.confirm,
                    tint = colors.textSecondary.copy(alpha = if (confirmEnabled) 1f else DISABLED_ALPHA),
                )
            }
            PendingOverflow(labels, onSkip, onChangeAmount)
        }
    }
}

@Composable
private fun PendingOverflow(
    labels: PendingLabels,
    onSkip: (() -> Unit)?,
    onChangeAmount: (() -> Unit)?,
) {
    if (onSkip == null && onChangeAmount == null) return
    var expanded by remember { mutableStateOf(false) }
    // The menu is anchored to the Box (not nested in the button) so it opens under this row's button.
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag(TestTags.RECURRING_MORE)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = labels.more,
                tint = FinTrackTheme.colors.textSecondary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onChangeAmount != null) {
                DropdownMenuItem(
                    text = { Text(labels.changeAmount) },
                    onClick = {
                        expanded = false
                        onChangeAmount()
                    },
                    modifier = Modifier.testTag(TestTags.RECURRING_CHANGE),
                )
            }
            if (onSkip != null) {
                DropdownMenuItem(
                    text = { Text(labels.skip) },
                    onClick = {
                        expanded = false
                        onSkip()
                    },
                    modifier = Modifier.testTag(TestTags.RECURRING_SKIP),
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.5f
