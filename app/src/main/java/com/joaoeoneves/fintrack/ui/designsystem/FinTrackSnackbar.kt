package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val SnackbarShapeRadius = 20.dp

/** Snackbar host styled to the design: soft surface, 20dp corners, screen-edge margins. */
@Composable
fun FinTrackSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
            shape = RoundedCornerShape(SnackbarShapeRadius),
            containerColor = FinTrackTheme.colors.surface2,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.onSurface,
            dismissActionContentColor = FinTrackTheme.colors.textSecondary,
        )
    }
}
