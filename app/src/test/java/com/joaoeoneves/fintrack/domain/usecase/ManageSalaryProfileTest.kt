package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.MANAGED_BONUS
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SPREAD_FULL_PPM
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageSalaryProfileTest {
    private val labels = SalaryLineLabels()
    private val profile =
        SalaryProfile(baseGrossCents = 100_000, subsidySpreadPpm = SPREAD_FULL_PPM, bonusCents = 5_000)
    private val own = PlannedIncome("own", "Own", 123, order = 0)

    private val profiles = FakeSalaryProfileRepository()
    private val income = FakePlannedIncomeRepository(listOf(own))
    private val useCase = ManageSalaryProfile(profiles, income)

    @Test
    fun save_writesProfile_andUpsertsLines() =
        runTest {
            assertTrue(useCase.save(profile, labels).isSuccess)

            assertEquals(profile, profiles.profile)
            assertEquals(setOf("own", MANAGED_NET_SALARY, MANAGED_BONUS), income.lines.map { it.id }.toSet())
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

            useCase.save(profile.copy(bonusCents = null), labels)

            assertEquals(setOf("own", MANAGED_NET_SALARY), income.lines.map { it.id }.toSet())
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
            assertEquals(116_667L, net.amountCents)
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
            assertEquals(3, income.lines.size)
        }
}
