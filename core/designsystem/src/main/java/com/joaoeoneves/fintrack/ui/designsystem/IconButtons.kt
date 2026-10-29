package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * The app's icon button: [contentDescription] on the button itself (one labelled control for screen readers),
 * foreground colour (or [tint]; dimmed when disabled) and a subtle press-scale.
 */
@Suppress("LongParameterList") // icon, label, click and the usual enabled/tint/modifier options
@Composable
fun FinTrackIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = tint,
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
