package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val ChipMinHeight = 40.dp
private val ChipIconSize = 18.dp
private val ChipDotSize = 10.dp

/**
 * Single-select chip: soft surface when idle, inverted (foreground-coloured) fill when [selected]. [leading] draws
 * before the label ([CategoryChipIcon] or [LegendDot]) and receives the chip's content colour.
 */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable (contentColor: Color) -> Unit)? = null,
) {
    val container = if (selected) MaterialTheme.colorScheme.onSurface else FinTrackTheme.colors.surface2
    val content = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .heightIn(min = ChipMinHeight)
                .clip(CircleShape)
                .background(container)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = Dimens.Space16),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(content)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A category icon for [CategoryChip.leading], tinted with the chip's content colour. */
@Composable
fun CategoryChipIcon(
    icon: ImageVector,
    tint: Color,
) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(ChipIconSize))
}

/** A small colour dot for [CategoryChip.leading]: the colour of the chart segment the chip stands for. */
@Composable
fun LegendDot(color: Color) {
    Box(Modifier.size(ChipDotSize).clip(CircleShape).background(color))
}
