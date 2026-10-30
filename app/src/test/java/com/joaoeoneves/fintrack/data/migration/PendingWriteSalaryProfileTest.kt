package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncome
import com.joaoeoneves.fintrack.data.local.LocalSalaryExtra
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

    @Test
    fun legacyGuestProfile_isWrittenAsExtras_withNoLegacyFields() {
        val legacy =
            LocalSalaryProfile(
                baseGrossCents = 100_000,
                flexPpm = 70_000,
                profitShareMultiplierPpm = 500_000,
                profitShareMonths = listOf(4),
                bonusCents = 10_000,
                bonusMonth = 5,
            )

        val data =
            plan(profile = legacy)
                .toBatches()
                .single()
                .single()
                .data

        val extras = data["extras"] as List<*>
        assertEquals(listOf("flex", "profitshare", "bonus"), extras.map { (it as Map<*, *>)["id"] })
        assertEquals(listOf("Flexible benefits", "Profit share", "Bonus"), extras.map { (it as Map<*, *>)["name"] })
        listOf("flexPpm", "profitShareMultiplierPpm", "profitShareMonths", "bonusCents", "bonusMonth")
            .forEach { assertFalse(it, data.containsKey(it)) }
    }

    @Test
    fun legacyGuestProfile_migratesTheBonusWithItsEnglishName() {
        val legacy = LocalSalaryProfile(baseGrossCents = 100_000, bonusCents = 10_000)

        val data =
            plan(profile = legacy)
                .toBatches()
                .single()
                .single()
                .data

        assertEquals("Bonus", ((data["extras"] as List<*>).single() as Map<*, *>)["name"])
    }

    @Test
    fun v10GuestProfile_extrasAreCarriedOver() {
        val extra = LocalSalaryExtra("e1", "Prize", "FIXED_CENTS", 5_000, listOf(3, 9), "IRS_SS", 1_000)

        val data =
            plan(profile = LocalSalaryProfile(baseGrossCents = 1, extras = listOf(extra)))
                .toBatches()
                .single()
                .single()
                .data

        val written = (data["extras"] as List<*>).single() as Map<*, *>
        assertEquals("Prize", written["name"])
        assertEquals(listOf(3, 9), written["months"])
        assertEquals("IRS_SS", written["deductions"])
    }

    @Test
    fun profileWithoutExtrasOrLegacy_writesAnEmptyExtrasList() {
        val data =
            plan(profile = LocalSalaryProfile(baseGrossCents = 1))
                .toBatches()
                .single()
                .single()
                .data

        assertEquals(emptyList<Any>(), data["extras"])
    }
}
