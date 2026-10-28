package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val EmptyIconSize = 40.dp

/** Centred empty/error placeholder: optional [icon], [title], optional secondary [message] and [action]. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(Dimens.Space32),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = FinTrackTheme.colors.textSecondary,
                    modifier = Modifier.size(EmptyIconSize),
                )
            }
            Text(text = title, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinTrackTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null) {
                Box(modifier = Modifier.padding(top = Dimens.Space8)) { action() }
            }
        }
    }
}
