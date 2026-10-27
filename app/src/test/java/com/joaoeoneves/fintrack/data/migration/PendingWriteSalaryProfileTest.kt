package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncome
import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PendingWriteSalaryProfileTest {
    private fun plan(
        plannedIncome: List<LocalPlannedIncome> = emptyList(),
        profile: LocalSalaryProfile? = null,
    ) = MigrationPlan(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        plannedIncome,
        emptyList(),
        0,
        salaryProfile = profile,
    )

    @Test
    fun profile_isOneSingleDocWriteCommittedLast() {
        val batches = plan(listOf(localPlanned("p1")), LocalSalaryProfile(baseGrossCents = 100_000)).toBatches()

        assertEquals(listOf("plannedIncome", "plan"), batches.map { it.single().collection })
        val write = batches.last().single()
        assertEquals("salaryProfile", write.id)
        assertEquals(100_000L, write.data["baseGrossCents"])
        assertEquals("GROSS", write.data["inputMode"])
    }

    @Test
    fun noProfile_noPlanWrite() {
        assertTrue(plan(listOf(localPlanned("p1"))).toBatches().none { it.single().collection == "plan" })
    }

    @Test
    fun unsetOptionalFields_areAbsent_notNull() {
        val data =
            plan(profile = LocalSalaryProfile(baseGrossCents = 1))
                .toBatches()
                .single()
                .single()
                .data

        assertFalse(data.containsKey("netMonthlyCents"))
        assertFalse(data.containsKey("flexPpm"))
        assertFalse(data.containsKey("bonusMonth"))
    }

    @Test
    fun managedKey_isWrittenOnlyWhenSet() {
        val managed = localPlanned("profile_meal").let { it.copy(managedKey = "profile_meal") }

        val writes = plan(listOf(managed, localPlanned("own"))).toBatches().single()

        assertEquals("profile_meal", writes.first { it.id == "profile_meal" }.data["managedKey"])
        assertFalse(writes.first { it.id == "own" }.data.containsKey("managedKey"))
    }
}
