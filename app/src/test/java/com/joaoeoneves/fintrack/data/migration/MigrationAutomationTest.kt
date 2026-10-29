package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalAutomation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The automation settings singleton in a guest-to-account migration: the account wins, else the guest's is copied. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigrationAutomationTest {
    private val automation = LocalAutomation(lineThrough = mapOf("p1" to "2026-09"), salaryPaysNextMonth = true)

    private fun guestDoc() = localDoc().copy(plannedIncome = listOf(localPlanned("p1")), automation = automation)

    @Test
    fun emptyAccount_copiesTheGuestSettings_andCountsOne() {
        val plan = planMigration(guestDoc(), remoteSnapshot())

        assertEquals(automation, plan.automation)
        assertEquals(2, plan.total)
    }

    @Test
    fun accountWithSettings_wins_nothingCopied() {
        val plan =
            planMigration(
                guestDoc(),
                remoteSnapshot().copy(hasAutomation = true),
            )

        assertNull(plan.automation)
        assertEquals(1, plan.total)
    }

    @Test
    fun invalidGuestCursor_isNotCopied() {
        val bad = automation.copy(lineThrough = mapOf("p1" to "soon"))

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
        val plan = planMigration(guestDoc(), remoteSnapshot())

        val write = plan.toBatches().flatten().single { it.id == "automation" }

        assertEquals("plan", write.collection)
        assertEquals(mapOf("p1" to "2026-09"), write.data["lineThrough"])
        assertFalse(write.data.containsKey("paydayEnabled"))
        assertFalse(write.data.containsKey("paydayThrough"))
    }
}
