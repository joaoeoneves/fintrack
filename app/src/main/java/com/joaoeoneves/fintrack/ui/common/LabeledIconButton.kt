package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.joaoeoneves.fintrack.ui.designsystem.pressScale
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * An [IconButton] whose [contentDescription] is set on the button itself rather than on the inner
 * icon, so screen readers announce a single labelled button instead of a button plus an image.
 * Monochrome (foreground colour, dimmed when disabled) with a subtle press-scale.
 */
@Composable
fun LabeledIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = FinTrackTheme.colors.textSecondary,
            ),
        modifier =
            modifier
                .pressScale(interactionSource)
                .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(icon, contentDescription = null)
    }
}
