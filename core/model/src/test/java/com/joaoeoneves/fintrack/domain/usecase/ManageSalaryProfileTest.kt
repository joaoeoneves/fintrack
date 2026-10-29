package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.MANAGED_DUODECIMOS
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SPREAD_FULL_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.managedExtraKey
import com.joaoeoneves.fintrack.testutil.bonusExtra
import com.joaoeoneves.fintrack.testutil.flexExtra
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageSalaryProfileTest {
    private val labels = SalaryLineLabels()
    private val profile =
        SalaryProfile(
            baseGrossCents = 100_000,
            subsidySpreadPpm = SPREAD_FULL_PPM,
            extras = listOf(bonusExtra(cents = 5_000)),
        )
    private val own = PlannedIncome("own", "Own", 123, order = 0)
    private val bonusKey = managedExtraKey("bonus", 5)

    private val profiles = FakeSalaryProfileRepository()
    private val income = FakePlannedIncomeRepository(listOf(own))
    private val useCase = ManageSalaryProfile(profiles, income)

    @Test
    fun save_writesProfile_andUpsertsLines() =
        runTest {
            assertTrue(useCase.save(profile, labels).isSuccess)

            assertEquals(profile, profiles.profile)
            val expected = setOf("own", MANAGED_NET_SALARY, MANAGED_DUODECIMOS, bonusKey)
            assertEquals(expected, income.lines.map { it.id }.toSet())
        }

    @Test
    fun save_withLegacyLines_upsertsTheExtraLines_andDeletesTheLegacyOnes() =
        runTest {
            val legacyKeys = listOf("profile_flex_q1", "profile_flex_q3", "profile_bonus")
            legacyKeys.forEach { income.savePlannedIncome(PlannedIncome(it, "old", 5, 50, managedKey = it)) }

            assertTrue(useCase.save(profile.copy(extras = listOf(flexExtra(), bonusExtra())), labels).isSuccess)

            val ids = income.lines.map { it.id }.toSet()
            assertTrue(legacyKeys.none { it in ids })
            assertTrue(bonusKey in ids)
            assertTrue(managedExtraKey("flex", 7) in ids)
        }

    @Test
    fun save_deletingAnExtra_deletesItsLines() =
        runTest {
            useCase.save(profile.copy(extras = listOf(flexExtra(), bonusExtra())), labels)

            useCase.save(profile.copy(extras = listOf(bonusExtra())), labels)

            val ids = income.lines.map { it.id }
            assertTrue(ids.none { it.startsWith("profile_extra_flex_") })
            assertTrue(bonusKey in ids)
        }

    @Test
    fun save_twice_isIdempotent() =
        runTest {
            useCase.save(profile, labels)
            val first = income.lines.toSet()

            useCase.save(profile, labels)

            assertEquals(first, income.lines.toSet())
        }

    @Test
    fun save_removesLinesTheProfileNoLongerProduces_keepsUserLines() =
        runTest {
            useCase.save(profile, labels)

            useCase.save(profile.copy(extras = emptyList()), labels)

            assertEquals(setOf("own", MANAGED_NET_SALARY, MANAGED_DUODECIMOS), income.lines.map { it.id }.toSet())
        }

    @Test
    fun save_updatesTheAmountButKeepsTheUsersLabel() =
        runTest {
            useCase.save(profile, labels)
            val renamed = income.lines.single { it.id == MANAGED_NET_SALARY }.copy(label = "Pay")
            income.savePlannedIncome(renamed)

            useCase.save(profile.copy(ssPpm = 0), labels)

            val net = income.lines.single { it.id == MANAGED_NET_SALARY }
            assertEquals("Pay", net.label)
            assertEquals(100_000L, net.amountCents)
        }

    @Test
    fun save_profileWithoutRequiredAmount_removesManagedLines() =
        runTest {
            useCase.save(profile, labels)

            assertTrue(useCase.save(SalaryProfile(), labels).isSuccess)

            assertEquals(listOf("own"), income.lines.map { it.id })
        }

    @Test
    fun save_profileFailure_isFailure_andWritesNoLines() =
        runTest {
            profiles.nextSaveError = IllegalStateException("boom")

            val result = useCase.save(profile, labels)

            assertTrue(result.isFailure)
            assertEquals(listOf("own"), income.lines.map { it.id })
        }

    @Test
    fun save_lineFailure_isFailure() =
        runTest {
            income.nextSaveError = IllegalStateException("boom")

            assertTrue(useCase.save(profile, labels).isFailure)
        }

    @Test
    fun delete_removesProfile_andAllManagedLines() =
        runTest {
            useCase.save(profile, labels)

            assertTrue(useCase.delete().isSuccess)

            assertNull(profiles.profile)
            assertEquals(listOf("own"), income.lines.map { it.id })
        }

    @Test
    fun delete_withoutProfile_isSuccess() =
        runTest {
            assertTrue(useCase.delete().isSuccess)

            assertEquals(listOf("own"), income.lines.map { it.id })
        }

    @Test
    fun delete_failure_isFailure_andKeepsLines() =
        runTest {
            useCase.save(profile, labels)
            profiles.nextDeleteError = IllegalStateException("boom")

            assertTrue(useCase.delete().isFailure)

            assertEquals(profile, profiles.profile)
            assertEquals(4, income.lines.size)
        }

    @Test
    fun resync_withoutProfile_writesNothing() =
        runTest {
            val before = income.lines

            assertTrue(useCase.resync().isSuccess)

            assertEquals(before, income.lines)
        }

    @Test
    fun resync_withoutManagedLines_writesNothing() =
        runTest {
            profiles.saveSalaryProfile(profile)

            assertTrue(useCase.resync().isSuccess)

            assertEquals(listOf(own), income.lines)
        }

    @Test
    fun resync_fixesStaleLines_thenIsANoOp() =
        runTest {
            useCase.save(profile, labels)
            income.deletePlannedIncome(MANAGED_DUODECIMOS)
            income.savePlannedIncome(income.lines.single { it.id == MANAGED_NET_SALARY }.copy(amountCents = 1))

            assertTrue(useCase.resync().isSuccess)
            val fixed = income.lines.toSet()

            assertTrue(fixed.any { it.id == MANAGED_DUODECIMOS })
            assertTrue(useCase.resync().isSuccess)
            assertEquals(fixed, income.lines.toSet())
        }

    @Test
    fun resync_stopsAtTheFirstFailure() =
        runTest {
            useCase.save(profile, labels)
            income.deletePlannedIncome(MANAGED_DUODECIMOS)
            income.deletePlannedIncome(bonusKey)
            income.nextSaveError = IllegalStateException("boom")

            assertTrue(useCase.resync().isFailure)

            assertTrue(income.lines.none { it.id == MANAGED_DUODECIMOS || it.id == bonusKey })
        }
}
