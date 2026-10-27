package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalMonthSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigrationPlannerMonthSettingsTest {
    private val oct = LocalMonthSettings("2026-10", 15)
    private val nov = LocalMonthSettings("2026-11", 0)

    private fun local(vararg settings: LocalMonthSettings) = localDoc().copy(monthSettings = settings.toList())

    @Test
    fun emptyAccount_copiesAllMonthSettings() {
        val plan = planMigration(local(oct, nov), remoteSnapshot())

        assertEquals(listOf(oct, nov), plan.monthSettings)
        assertEquals(2, plan.total)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun accountWinsPerMonth_theOtherMonthsAreStillCopied() {
        val plan = planMigration(local(oct, nov), remoteSnapshot(monthSettingsIds = setOf("2026-10")))

        assertEquals(listOf(nov), plan.monthSettings)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun accountHasAll_nothingToCopy() {
        val plan = planMigration(local(oct), remoteSnapshot(monthSettingsIds = setOf("2026-10")))

        assertTrue(plan.monthSettings.isEmpty())
        assertEquals(0, plan.total)
    }

    @Test
    fun invalidMonthSettings_areSkippedAndCounted() {
        val bad = listOf(LocalMonthSettings("2026-12", 99), LocalMonthSettings("x", 1))
        val plan = planMigration(local(oct, *bad.toTypedArray()), remoteSnapshot())

        assertEquals(listOf(oct), plan.monthSettings)
        assertEquals(2, plan.skipped)
    }

    @Test
    fun replanningAfterCommit_returnsNothing() {
        val remote = remoteSnapshot()
        val first = planMigration(local(oct, nov), remote)

        val second = planMigration(local(oct, nov), remote.after(first))

        assertTrue(second.monthSettings.isEmpty())
    }

    @Test
    fun pendingWrites_useMonthAsIdAndWorkingDaysField_andComeLast() {
        val plan = planMigration(local(oct).copy(income = listOf(localIncome("i1"))), remoteSnapshot())

        val batches = plan.toBatches()

        assertEquals(listOf("income", "monthSettings"), batches.map { it.single().collection })
        val write = batches.last().single()
        assertEquals("2026-10", write.id)
        assertEquals(setOf("workingDays", "updatedAt"), write.data.keys)
        assertEquals(15, write.data["workingDays"])
    }

    @Test
    fun daysOffAndDaysOffOnlyRecords_areCarriedUnchanged() {
        val both = LocalMonthSettings("2026-10", 15, 2)
        val daysOffOnly = LocalMonthSettings("2026-11", null, 3)

        val plan = planMigration(local(both, daysOffOnly), remoteSnapshot())

        assertEquals(listOf(both, daysOffOnly), plan.monthSettings)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun pendingWrites_includeDaysOff_andOmitAbsentWorkingDays() {
        val plan =
            planMigration(
                local(LocalMonthSettings("2026-10", 15, 2), LocalMonthSettings("2026-11", null, 3)),
                remoteSnapshot(),
            )

        val writes = plan.toBatches().flatten()

        assertEquals(setOf("workingDays", "daysOff", "updatedAt"), writes[0].data.keys)
        assertEquals(2, writes[0].data["daysOff"])
        assertEquals(setOf("daysOff", "updatedAt"), writes[1].data.keys)
        assertEquals(3, writes[1].data["daysOff"])
    }

    @Test
    fun invalidDaysOff_andEmptyRecords_areSkipped() {
        val bad = listOf(LocalMonthSettings("2026-12", null, 24), LocalMonthSettings("2027-01", null, 0))

        val plan = planMigration(local(oct, *bad.toTypedArray()), remoteSnapshot())

        assertEquals(listOf(oct), plan.monthSettings)
        assertEquals(2, plan.skipped)
    }
}
