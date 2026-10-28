package com.joaoeoneves.fintrack.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only the parts of [TopLevelTab] reachable without a NavController: tab order/uniqueness and the
 * null-destination behaviour of the extensions. Resolving real destinations
 * (`isTopLevel`/`currentTab` through `hasRoute`) needs a built NavGraph and is left to instrumented tests.
 */
class TopLevelTabTest {
    @Test
    fun tabs_areHomeActivityPlanSavingsInsights_inOrder() {
        assertEquals(
            listOf(TopLevelTab.HOME, TopLevelTab.ACTIVITY, TopLevelTab.PLAN, TopLevelTab.SAVINGS, TopLevelTab.INSIGHTS),
            TopLevelTab.entries,
        )
    }

    @Test
    fun tabs_haveDistinctGraphs_labelsAndTestTags() {
        val tabs = TopLevelTab.entries
        assertEquals(tabs.size, tabs.map { it.graph }.toSet().size)
        assertEquals(tabs.size, tabs.map { it.labelRes }.toSet().size)
        assertEquals(tabs.size, tabs.map { it.testTag }.toSet().size)
    }

    @Test
    fun tabGraphs_areTheNestedGraphRoutes() {
        assertEquals(HomeGraph, TopLevelTab.HOME.graph)
        assertEquals(ActivityGraph, TopLevelTab.ACTIVITY.graph)
        assertEquals(PlanGraph, TopLevelTab.PLAN.graph)
        assertEquals(SavingsGraph, TopLevelTab.SAVINGS.graph)
        assertEquals(InsightsGraph, TopLevelTab.INSIGHTS.graph)
    }

    @Test
    fun nullDestination_isNotTopLevel_andHasNoTab() {
        assertFalse(null.isTopLevel())
        assertNull(null.currentTab())
        assertTrue(TopLevelTab.entries.isNotEmpty())
    }
}
