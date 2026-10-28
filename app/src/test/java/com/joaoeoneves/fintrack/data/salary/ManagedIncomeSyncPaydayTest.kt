package com.joaoeoneves.fintrack.data.salary

import android.app.Application
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSalaryLineLabelsProvider
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.MANAGED_NET_SALARY
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.paydayIncomeId
import com.joaoeoneves.fintrack.domain.model.salaryProfileLines
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.YearMonth

/** Counts [updateIncome] calls on the wrapped repository. */
private class CountingIncomes(
    private val delegate: FakeIncomeRepository,
) : IncomeRepository by delegate {
    var updates = 0

    override suspend fun updateIncome(income: Income): Result<Unit> {
        updates++
        return delegate.updateIncome(income)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ManagedIncomeSyncPaydayTest {
    private val en = FakeSalaryLineLabelsProvider.ENGLISH
    private val profile = SalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000)
    private val own = PlannedIncome("own", "Own", 123, order = 0)
    private val lines = salaryProfileLines(profile, listOf(own), en)
    private val oct = YearMonth.of(2026, 10)

    private fun income(
        id: String,
        source: String,
    ) = Income(id, source, 1_000, Instant.parse("2026-10-30T12:00:00Z"), null)

    private val netId = paydayIncomeId(MANAGED_NET_SALARY, oct)

    @Test
    fun repairsPaydayIncomesOnce_andNotOnTheNextEmission() =
        runTest(UnconfinedTestDispatcher()) {
            val seeded = listOf(income(netId, "Net salary"), income("pay_own_202610", "Net salary"))
            val incomes = FakeIncomeRepository(seeded)
            val counting = CountingIncomes(incomes)
            val plan = FakePlannedIncomeRepository(lines + own)
            val labels = FakeSalaryLineLabelsProvider(FakeSalaryLineLabelsProvider.PORTUGUESE)

            ManagedIncomeSync(FakeSalaryProfileRepository(profile), plan, counting, labels, backgroundScope).start()
            advanceUntilIdle()

            val byId = incomes.getAllIncome().getOrThrow().associate { it.id to it.source }
            assertEquals("Salário líquido", byId[netId])
            assertEquals("Net salary", byId["pay_own_202610"])
            assertEquals(1, counting.updates)

            // The user edits the source back; an unrelated plan change must not trigger another repair.
            incomes.updateIncome(income(netId, "Net salary"))
            plan.savePlannedIncome(PlannedIncome("another", "Another", 5, order = 9))
            advanceUntilIdle()

            assertEquals(1, counting.updates)
            assertEquals("Net salary", incomes.getIncome(netId).getOrThrow()?.source)
        }

    @Test
    fun alreadyLocalized_writesNothing() =
        runTest(UnconfinedTestDispatcher()) {
            val incomes = FakeIncomeRepository(listOf(income(netId, "Salário líquido")))
            val counting = CountingIncomes(incomes)
            val labels = FakeSalaryLineLabelsProvider(FakeSalaryLineLabelsProvider.PORTUGUESE)

            ManagedIncomeSync(
                FakeSalaryProfileRepository(profile),
                FakePlannedIncomeRepository(lines + own),
                counting,
                labels,
                backgroundScope,
            ).start()
            advanceUntilIdle()

            assertEquals(0, counting.updates)
        }

    @Test
    fun relabelsTheStoredLinesToo() =
        runTest(UnconfinedTestDispatcher()) {
            val plan = FakePlannedIncomeRepository(lines + own)
            val labels = FakeSalaryLineLabelsProvider(FakeSalaryLineLabelsProvider.PORTUGUESE)

            ManagedIncomeSync(
                FakeSalaryProfileRepository(profile),
                plan,
                FakeIncomeRepository(),
                labels,
                backgroundScope,
            ).start()
            advanceUntilIdle()

            assertEquals("Salário líquido", plan.lines.single { it.id == MANAGED_NET_SALARY }.label)
            assertEquals("Own", plan.lines.single { it.id == "own" }.label)
        }
}
