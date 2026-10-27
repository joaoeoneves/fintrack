package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.TestTags
import com.joaoeoneves.fintrack.ui.designsystem.pressScale
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion

private const val SCROLL_THRESHOLD_PX = 6f
private const val ENTER_MS = 240
private const val EXIT_MS = 180
private val PillShadow = 8.dp
private val PillInnerHeight = 44.dp
private val CapsulePadding = 6.dp
private val PlusIconSize = 18.dp

/**
 * Tracks whether the add-transaction pill should be shown: it hides while the user scrolls down
 * and comes back on scrolling up. Attach [connection] to the scrolling list via `nestedScroll`.
 */
@Stable
class PillVisibility {
    var visible by mutableStateOf(true)
        private set

    /** Brings the pill back, e.g. when the list has been scrolled to its end. */
    fun show() {
        visible = true
    }

    val connection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                when {
                    available.y < -SCROLL_THRESHOLD_PX -> visible = false
                    available.y > SCROLL_THRESHOLD_PX -> visible = true
                }
                return Offset.Zero
            }
        }
}

/**
 * Floating capsule with the two primary actions, "+ Expense" and "+ Income". Each action is exposed
 * to accessibility as one node ("Add expense" / "Add income"); the short visible labels are hidden
 * from semantics so they never collide with other "Income"/"Expense" text. Slides away while
 * [visible] is false (always shown under reduced motion).
 */
@Composable
fun AddTransactionPill(
    visible: Boolean,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible || reducedMotion,
        enter = slideInVertically(tween(ENTER_MS)) { it / 2 } + fadeIn(tween(ENTER_MS)),
        exit = slideOutVertically(tween(EXIT_MS)) { it / 2 } + fadeOut(tween(EXIT_MS)),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = FinTrackTheme.colors.surface2,
            border = BorderStroke(Dimens.HairlineThickness, FinTrackTheme.colors.hairline),
            shadowElevation = PillShadow,
        ) {
            Row(
                modifier = Modifier.padding(CapsulePadding),
                horizontalArrangement = Arrangement.spacedBy(CapsulePadding),
            ) {
                PillAction(
                    label = stringResource(R.string.dashboard_add_expense_short),
                    description = stringResource(R.string.action_add_expense),
                    filled = true,
                    onClick = onAddExpense,
                    modifier = Modifier.testTag(TestTags.DASHBOARD_ADD_EXPENSE),
                )
                PillAction(
                    label = stringResource(R.string.dashboard_add_income_short),
                    description = stringResource(R.string.action_add_income),
                    filled = false,
                    onClick = onAddIncome,
                )
            }
        }
    }
}

@Composable
private fun PillAction(
    label: String,
    description: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .pressScale(interactionSource)
                .height(PillInnerHeight)
                .clip(CircleShape)
                .background(if (filled) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = Dimens.Space16),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(PlusIconSize),
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}
