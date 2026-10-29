package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val ProgressSize = 22.dp
private val ProgressStroke = 2.dp
private val GhostMinHeight = 48.dp

/**
 * Full-width black-on-white (inverted in dark) pill: the one primary action of a screen. While
 * [loading] a spinner replaces the label, or the [leadingIcon] when one is given.
 */
@Suppress("LongParameterList") // text/click/modifier/enabled plus the two loading-state slots
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = primary,
                contentColor = onPrimary,
                disabledContainerColor = if (loading) primary else FinTrackTheme.colors.surface2,
                disabledContentColor = if (loading) onPrimary else FinTrackTheme.colors.textSecondary,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.PillButtonHeight)
                .pressScale(interactionSource),
    ) {
        // With a leading icon the label stays visible while loading (the spinner replaces the icon).
        val showLabel = !loading || leadingIcon != null
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ProgressSize),
                    strokeWidth = ProgressStroke,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                leadingIcon?.invoke()
            }
            if (showLabel) Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Secondary pill on the soft surface tone, for actions that should not compete with the primary. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = FinTrackTheme.colors.surface2,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = FinTrackTheme.colors.surface2,
                disabledContentColor = FinTrackTheme.colors.textSecondary,
            ),
        modifier = modifier.heightIn(min = GhostMinHeight).pressScale(interactionSource),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Text-only button in the negative colour for destructive confirmations (delete, sign out). */
@Composable
fun DestructiveTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = FinTrackTheme.colors.negative),
        modifier = modifier,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** The standard text button of dialogs (confirm, cancel, retry): labelLarge, neutral colour. */
@Composable
fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
