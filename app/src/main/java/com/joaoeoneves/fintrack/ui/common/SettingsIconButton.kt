package com.joaoeoneves.fintrack.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R

/** The top-right Settings icon shown on every tab root. */
@Composable
fun SettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LabeledIconButton(
        icon = Icons.Default.Settings,
        contentDescription = stringResource(R.string.cd_settings),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.DASHBOARD_SETTINGS),
    )
}
