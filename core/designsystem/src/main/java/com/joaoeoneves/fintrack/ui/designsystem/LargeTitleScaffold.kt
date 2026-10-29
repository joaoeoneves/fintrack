package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.core.designsystem.R

/**
 * Scaffold with a collapsing large title (iOS-style) on the app background colour. [onBack], when
 * given, adds a back arrow with the standard "Back" description;
 * otherwise [navigationIcon] (e.g. an account avatar) is shown in that slot.
 */
@Suppress("LongParameterList") // scaffold slots; each one is independently optional
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        FinTrackIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            onClick = onBack,
                        )
                    } else {
                        navigationIcon?.invoke()
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
        content = content,
    )
}
