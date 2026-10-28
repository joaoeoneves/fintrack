package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.ui.graphics.vector.ImageVector

/** One entry of the [FinTrackNavigationBar]. */
data class NavigationBarEntry(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
)
