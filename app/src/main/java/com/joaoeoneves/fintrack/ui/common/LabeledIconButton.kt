package com.joaoeoneves.fintrack.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackIconButton

/** The labelled icon button of the design system (see [FinTrackIconButton]). */
@Composable
fun LabeledIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = FinTrackIconButton(icon, contentDescription, onClick, modifier, enabled)
