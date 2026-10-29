package com.joaoeoneves.fintrack.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navigation
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [navigateToTab] on a real controller (no composition): Home is popped back to, never duplicated. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NavigateToTabTest {
    private val empty: @Composable () -> Unit = {}

    private fun controller(): NavHostController {
        val nav = NavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.navigatorProvider.addNavigator(DialogNavigator())
        nav.graph =
            nav.createGraph(startDestination = HomeGraph) {
                navigation<HomeGraph>(startDestination = Home) { composable<Home> { empty() } }
                navigation<IncomeGraph>(startDestination = IncomeHome()) { composable<IncomeHome> { empty() } }
                composable<InvestmentDetail> { empty() }
            }
        return nav
    }

    private fun NavHostController.homeEntries() = currentBackStack.value.count { it.destination.hasRoute(Home::class) }

    @Test
    fun incomeThenHomeThenDetailThenBack_landsOnHome_withOneHomeEntry() {
        val nav = controller()

        nav.navigateToTab(TopLevelTab.INCOME)
        assertEquals(TopLevelTab.INCOME, nav.currentDestination.currentTab())
        nav.navigateToTab(TopLevelTab.HOME)
        assertTrue(nav.currentDestination!!.hasRoute(Home::class))
        nav.navigate(InvestmentDetail("month:2026-10"))
        assertTrue(nav.currentDestination!!.hasRoute(InvestmentDetail::class))

        assertTrue(nav.popBackStack())

        assertTrue(nav.currentDestination!!.hasRoute(Home::class))
        assertEquals(1, nav.homeEntries())
    }

    @Test
    fun homeTab_whileOnHome_staysOnHome() {
        val nav = controller()

        nav.navigateToTab(TopLevelTab.HOME)

        assertTrue(nav.currentDestination!!.hasRoute(Home::class))
        assertEquals(1, nav.homeEntries())
    }
}
