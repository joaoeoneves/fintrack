package com.joaoeoneves.fintrack.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val TARGET: String =
    InstrumentationRegistry.getArguments().getString("targetAppId") ?: "com.joaoeoneves.fintrack"

/** Generates the baseline and startup profiles; run via `:app:generateBaselineProfile`. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() =
        rule.collect(packageName = TARGET, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            enterApp()
            waitForDashboard()
        }

    @Test
    fun dashboardJourneys() =
        rule.collect(packageName = TARGET, includeInStartupProfile = false) {
            pressHome()
            startActivityAndWait()
            enterApp()
            waitForDashboard()
            flingDashboard()
            openAndBack(GeneratorTags.DASHBOARD_ADD_EXPENSE)
            openAndBack(GeneratorTags.DASHBOARD_SETTINGS)
            openAndBack(GeneratorTags.SEE_ALL_ACTIVITY, GeneratorTags.ACTIVITY_LIST)
            openTab(GeneratorTags.NAV_ACTIVITY)
            openTab(GeneratorTags.NAV_PLAN)
            openTab(GeneratorTags.NAV_INSIGHTS)
            openTab(GeneratorTags.NAV_HOME)
        }
}
