package com.joaoeoneves.fintrack.ui.common

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.joaoeoneves.fintrack.ui.designsystem.rememberFinTrackHaptics
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.indicatorSpring
import com.joaoeoneves.fintrack.ui.theme.motionSpec

private const val DELETE_THRESHOLD_FRACTION = 0.4f
private const val ICON_REST_SCALE = 0.7f

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
    val dismissState =
        rememberSwipeToDismissBoxState(
            positionalThreshold = { totalDistance -> totalDistance * DELETE_THRESHOLD_FRACTION },
        )
    val haptics = rememberFinTrackHaptics()
    var pastThreshold by remember { mutableStateOf(false) }

    // Tick once each time the drag crosses the delete threshold, in either direction.
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.targetValue == SwipeToDismissBoxValue.EndToStart }
            .collect { past ->
                if (past && !pastThreshold) haptics.tick()
                pastThreshold = past
            }
    }

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            haptics.confirm()
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
            DeleteBackground(
                revealed = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart,
                pastThreshold = pastThreshold,
            )
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

/** The red band behind a swiped row; its trash icon scales up once the drag passes the delete threshold. */
@Composable
private fun DeleteBackground(
    revealed: Boolean,
    pastThreshold: Boolean,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (pastThreshold) 1f else ICON_REST_SCALE,
        animationSpec = motionSpec(indicatorSpring()),
        label = "swipeDeleteIconScale",
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (revealed) FinTrackTheme.colors.negative else Color.Transparent)
                .padding(horizontal = Dimens.ScreenPadding),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (revealed) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
    }
}
