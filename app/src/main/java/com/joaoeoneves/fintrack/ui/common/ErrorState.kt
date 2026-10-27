package com.joaoeoneves.fintrack.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton

/**
 * A centered error message with a retry button, shared between screens that observe a Firestore
 * flow which can complete exceptionally (e.g. dashboard and full expense list).
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = stringResource(R.string.error_state_title),
        message = message,
        icon = Icons.Outlined.ErrorOutline,
        action = { GhostButton(text = stringResource(R.string.action_retry), onClick = onRetry) },
        modifier = modifier,
    )
}
