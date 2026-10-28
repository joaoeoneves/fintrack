package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/**
 * The app's bottom navigation bar: background colour of the app with a hairline on top, no
 * indicator pill, labels always shown. Gold (`accentText`) marks only the selected entry.
 */
@Composable
fun FinTrackNavigationBar(
    entries: List<NavigationBarEntry>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FinTrackTheme.colors
    val itemColors =
        NavigationBarItemDefaults.colors(
            selectedIconColor = colors.accentText,
            selectedTextColor = colors.accentText,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = Color.Transparent,
        )
    Box(modifier = modifier) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            entries.forEachIndexed { index, entry ->
                NavigationBarItem(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    icon = { Icon(entry.icon, contentDescription = null) },
                    label = { Text(entry.label) },
                    alwaysShowLabel = true,
                    colors = itemColors,
                    modifier = Modifier.testTag(entry.testTag),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(Dimens.HairlineThickness)
                    .background(colors.hairline),
        )
    }
}
