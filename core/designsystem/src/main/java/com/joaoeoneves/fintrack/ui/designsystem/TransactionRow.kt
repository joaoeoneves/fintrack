package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.joaoeoneves.fintrack.ui.theme.Amount
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * One transaction line: monochrome icon bubble, title with an optional [badge] beside it, a
 * secondary [subtitle] and a right-aligned amount. The children are plain `Text`s (no semantics
 * clearing) so tests and screen readers can address the title, subtitle and amount separately.
 * Horizontal screen padding is the caller's responsibility.
 */
@Suppress("LongParameterList") // one slot per visual part of a row; bundling them would only add indirection
@Composable
fun TransactionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    amountText: String,
    amountColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    subtitleMaxLines: Int = 1,
    /** Long press, e.g. to start a multi-selection. */
    onLongClick: (() -> Unit)? = null,
    /** Drawn as part of a multi-selection: soft background and a check in place of the icon. */
    selected: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (selected) Modifier.background(FinTrackTheme.colors.surface2) else Modifier)
                .then(
                    if (onClick != null) {
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    } else {
                        Modifier
                    },
                ).padding(vertical = Dimens.Space12),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(Dimens.IconBubble)
                    .clip(CircleShape)
                    .background(FinTrackTheme.colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.Check else icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) {
                    Box(modifier = Modifier.padding(start = Dimens.Space8)) { badge() }
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(text = amountText, style = Amount, color = amountColor, maxLines = 1)
    }
}
