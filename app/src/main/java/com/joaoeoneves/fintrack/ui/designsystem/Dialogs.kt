package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val SheetTopRadius = 28.dp

/** [AlertDialog] on the soft surface tone with 28dp corners. */
@Suppress("LongParameterList") // forwards the AlertDialog slots it restyles
@Composable
fun FinTrackAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        properties = properties,
        shape = MaterialTheme.shapes.large,
        containerColor = FinTrackTheme.colors.surface2,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = FinTrackTheme.colors.textSecondary,
    )
}

/**
 * Modal bottom sheet for editors: 28dp top corners, base surface tone, content lifted above the
 * keyboard and the navigation bar. Opens fully expanded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinTrackBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = SheetTopRadius, topEnd = SheetTopRadius),
        containerColor = FinTrackTheme.colors.surface1,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = Dimens.ScreenPadding)
                    .padding(bottom = Dimens.Space16),
            content = content,
        )
    }
}
