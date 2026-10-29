package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * Shows a short message on the app shell's snackbar host, which outlives the screen that asked (e.g. one that
 * navigates away straight after). A no-op outside the shell.
 */
val LocalAppSnackbar = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * Shows an action snackbar (e.g. "Desfazer") that times out. Material's default for a snackbar with an action
 * is Indefinite, which would leave undo prompts on screen forever.
 */
suspend fun SnackbarHostState.showActionSnackbar(
    message: String,
    actionLabel: String,
): SnackbarResult =
    showSnackbar(
        message = message,
        actionLabel = actionLabel,
        duration = SnackbarDuration.Long,
    )
