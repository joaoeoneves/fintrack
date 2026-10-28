package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeGoalRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.StartStep
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [ObserveStartChecklist] wired over fake repositories. */
class ObserveStartChecklistTest {
    private val profiles = FakeSalaryProfileRepository()
    private val plan = FakePlannedIncomeRepository()
    private val envelopes = FakeEnvelopeRepository()
    private val recurring = FakeRecurringRepository()
    private val goals = FakeGoalRepository()
    private val expenses = FakeExpenseRepository()
    private val useCase = ObserveStartChecklist(profiles, plan, envelopes, recurring, goals, expenses)

    @Test
    fun emptyRepositories_nothingDone() =
        runTest {
            assertTrue(useCase().first().done.isEmpty())
        }

    @Test
    fun reflectsEachRepository() =
        runTest {
            profiles.saveSalaryProfile(SalaryProfile(baseGrossCents = 100_000L))
            plan.savePlannedIncome(PlannedIncome("l1", "Pay", 100_000L, 0, autoRecord = true))
            envelopes.addEnvelope("Home")
            recurring.saveRecurring(recurringItem(id = ""))
            expenses.addExpense(
                Expense("e1", "Coffee", 300L, BuiltInCategories.FOOD, Instant.parse("2026-10-05T10:00:00Z"), null),
            )

            val result = useCase().first()

            assertEquals(
                setOf(
                    StartStep.SALARY,
                    StartStep.ENVELOPES,
                    StartStep.BILLS,
                    StartStep.PAYDAY,
                    StartStep.FIRST_EXPENSE,
                ),
                result.done,
            )
        }

    @Test
    fun updatesWhenAnExpenseIsAdded() =
        runTest {
            assertTrue(StartStep.FIRST_EXPENSE !in useCase().first().done)

            expenses.addExpense(
                Expense("e1", "Coffee", 300L, BuiltInCategories.FOOD, Instant.parse("2026-10-05T10:00:00Z"), null),
            )

            assertTrue(StartStep.FIRST_EXPENSE in useCase().first().done)
        }
}
