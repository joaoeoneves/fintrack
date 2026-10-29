package com.joaoeoneves.fintrack.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptionsBuilder

/**
 * Switches to [tab], keeping every tab's own back stack and state. Selecting the tab that is
 * already showing pops back to its root instead.
 */
fun NavController.navigateToTab(tab: TopLevelTab) {
    if (currentDestination.currentTab() == tab) {
        val tabGraph = graph.findNode(tab.graph) as? NavGraph
        tabGraph?.let { popBackStack(it.findStartDestination().id, inclusive = false) }
        return
    }
    // Home is the bottom of the stack: pop back to it (saving the tab left behind) instead of navigating to its graph,
    // which could leave a second Home entry under a detail page and make back leave the app.
    if (tab == TopLevelTab.HOME && popBackStack<Home>(inclusive = false, saveState = true)) return
    navigate(tab.graph) { tabOptions(this@navigateToTab, restoreState = true) }
}

/**
 * Opens a tab root with explicit arguments (a deep link from a Home card). Unlike [navigateToTab]
 * the tab's saved state is not restored, so the requested filter wins over earlier choices.
 */
fun NavController.navigateToTabWith(route: Any) {
    navigate(route) { tabOptions(this@navigateToTabWith, restoreState = false) }
}

private fun NavOptionsBuilder.tabOptions(
    navController: NavController,
    restoreState: Boolean,
) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    this.restoreState = restoreState
}
