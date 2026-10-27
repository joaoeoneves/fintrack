package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalPlannedIncome
import com.joaoeoneves.fintrack.data.local.LocalSalaryProfile
import com.joaoeoneves.fintrack.domain.model.MANAGED_MEAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPlannerSalaryProfileTest {
    private val profile = LocalSalaryProfile(baseGrossCents = 100_000)
    private val managed =
        LocalPlannedIncome("profile_meal", "Meal", 900, 0, basis = "PER_WORKING_DAY", managedKey = MANAGED_MEAL)
    private val own = localPlanned("own", order = 1)

    @Test
    fun emptyAccount_copiesProfile_andKeepsManagedKeys() {
        val local = localDoc().copy(salaryProfile = profile, plannedIncome = listOf(managed, own))

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(profile, plan.salaryProfile)
        assertEquals(MANAGED_MEAL, plan.plannedIncome.first { it.id == "profile_meal" }.managedKey)
        assertEquals(3, plan.total)
    }

    @Test
    fun accountWithProfile_wins_profileNotCopied_managedKeysStripped() {
        val local = localDoc().copy(salaryProfile = profile, plannedIncome = listOf(managed, own))

        val plan = planMigration(local, remoteSnapshot().copy(hasSalaryProfile = true))

        assertNull(plan.salaryProfile)
        assertEquals(listOf(null, null), plan.plannedIncome.map { it.managedKey })
        assertEquals(2, plan.plannedIncome.size)
    }

    @Test
    fun noGuestProfile_stripsManagedKeys() {
        val plan = planMigration(localDoc().copy(plannedIncome = listOf(managed)), remoteSnapshot())

        assertNull(plan.salaryProfile)
        assertNull(plan.plannedIncome.single().managedKey)
    }

    @Test
    fun invalidGuestProfile_isNotCopied_andManagedKeysStripped() {
        val local = localDoc().copy(salaryProfile = profile.copy(ssPpm = 5_000_000), plannedIncome = listOf(managed))

        val plan = planMigration(local, remoteSnapshot())

        assertNull(plan.salaryProfile)
        assertNull(plan.plannedIncome.single().managedKey)
    }

    @Test
    fun noProfileAnywhere_planIsEmpty() {
        val plan = planMigration(localDoc(), remoteSnapshot())

        assertNull(plan.salaryProfile)
        assertEquals(0, plan.total)
        assertTrue(plan.plannedIncome.isEmpty())
    }
}
