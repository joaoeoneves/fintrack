package com.joaoeoneves.fintrack.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import com.joaoeoneves.fintrack.ui.theme.Motion
import com.joaoeoneves.fintrack.ui.theme.motionSpec

/**
 * Scaffold shared by the top-level tabs (Home, Activity, Income, Spending): the account avatar with the tab's
 * [title] beside it on a fixed header, then [headerExtra] (Home puts its period selector there). A hairline fades in
 * along the header once the content is [scrolled] under it.
 */
@Suppress("LongParameterList") // scaffold slots; each one is independently optional
@Composable
fun TabScaffold(
    title: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    scrolled: Boolean = false,
    headerExtra: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TabHeader(title, onOpenSettings, scrolled, headerExtra) },
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = FabPosition.Center,
        content = content,
    )
}

@Composable
private fun TabHeader(
    title: String,
    onOpenSettings: () -> Unit,
    scrolled: Boolean,
    extra: @Composable () -> Unit,
) {
    val separator by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = motionSpec(tween(Motion.Short)),
        label = "tabHeaderSeparator",
    )
    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(onClick = onOpenSettings)
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = Dimens.Space8),
                )
            }
            extra()
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(Dimens.HairlineThickness)
                    .graphicsLayer { alpha = separator }
                    .background(FinTrackTheme.colors.hairline),
        )
    }
}
