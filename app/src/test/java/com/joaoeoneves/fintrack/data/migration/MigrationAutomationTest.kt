package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalAutomation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The automation settings singleton in a guest-to-account migration: the account wins, else the guest's is copied. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigrationAutomationTest {
    private val automation = LocalAutomation(paydayEnabled = true, paydayThrough = "2026-09")

    @Test
    fun emptyAccount_copiesTheGuestSettings_andCountsOne() {
        val plan = planMigration(localDoc().copy(automation = automation), remoteSnapshot())

        assertEquals(automation, plan.automation)
        assertEquals(1, plan.total)
    }

    @Test
    fun accountWithSettings_wins_nothingCopied() {
        val plan =
            planMigration(localDoc().copy(automation = automation), remoteSnapshot().copy(hasAutomation = true))

        assertNull(plan.automation)
        assertEquals(0, plan.total)
    }

    @Test
    fun invalidGuestCursor_isNotCopied() {
        val bad = automation.copy(paydayThrough = "soon")

        assertNull(planMigration(localDoc().copy(automation = bad), remoteSnapshot()).automation)
    }

    @Test
    fun noGuestSettings_nothingPlanned() {
        val plan = planMigration(localDoc(), remoteSnapshot())

        assertNull(plan.automation)
        assertEquals(0, plan.total)
    }

    @Test
    fun copiedSettings_areOneSingleDocWrite_inThePlanCollection() {
        val plan = planMigration(localDoc().copy(automation = automation), remoteSnapshot())

        val write = plan.toBatches().flatten().single { it.id == "automation" }

        assertEquals("plan", write.collection)
        assertEquals(true, write.data["paydayEnabled"])
        assertEquals("2026-09", write.data["paydayThrough"])
    }

    @Test
    fun enabledWithoutCursor_omitsTheCursorField() {
        val plan = planMigration(localDoc().copy(automation = LocalAutomation(true)), remoteSnapshot())

        val write = plan.toBatches().flatten().single { it.id == "automation" }

        assertTrue(!write.data.containsKey("paydayThrough"))
    }
}
