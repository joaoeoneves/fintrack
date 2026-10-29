package com.joaoeoneves.fintrack.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.TestTags

/**
 * The bottom-navigation tabs. Each owns a nested graph ([graph]) with its own back stack. This
 * enum is never a route argument, so it is safe under R8.
 */
enum class TopLevelTab(
    val graph: Any,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val testTag: String,
) {
    HOME(HomeGraph, R.string.nav_home, Icons.Default.Home, TestTags.NAV_HOME),
    ACTIVITY(ActivityGraph, R.string.nav_activity, Icons.AutoMirrored.Filled.ReceiptLong, TestTags.NAV_ACTIVITY),
    INCOME(IncomeGraph, R.string.nav_income, Icons.Default.AccountBalanceWallet, TestTags.NAV_INCOME),
    SPENDING(SpendingGraph, R.string.nav_spending, Icons.Default.ShoppingCart, TestTags.NAV_SPENDING),
}

/** Whether this destination is one of the tab roots (the bottom bar shows only there). */
fun NavDestination?.isTopLevel(): Boolean = this?.hierarchy?.any { it.isTabRoot() } == true

/** The tab whose graph contains this destination, or `null` outside the tab graphs (forms, Settings). */
fun NavDestination?.currentTab(): TopLevelTab? =
    this?.hierarchy?.let { chain ->
        TopLevelTab.entries.firstOrNull { tab -> chain.any { it.hasRoute(tab.graph::class) } }
    }

private fun NavDestination.isTabRoot(): Boolean {
    val roots = listOf(hasRoute<Home>(), hasRoute<Activity>(), hasRoute<IncomeHome>(), hasRoute<SpendingHome>())
    return roots.any { it }
}
