package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * A swipe-to-delete row shared between the expense and income list screens. Swiping from the end
 * towards the start (right-to-left in LTR) reveals a red band and invokes [onDelete]; there is
 * deliberately no on-row delete button. Assistive technologies get a custom action labelled
 * [deleteLabel] instead of the gesture.
 */
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    deleteLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier =
            modifier.semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            onDelete()
                            true
                        },
                    )
            },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val revealed = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(if (revealed) FinTrackTheme.colors.negative else Color.Transparent)
                        .padding(horizontal = Dimens.ScreenPadding),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (revealed) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                }
            }
        },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
