package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.PlannedSource
import com.joaoeoneves.fintrack.domain.repository.EnvelopeEdit
import com.joaoeoneves.fintrack.testutil.assertFailsWithError
import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The envelopes and recurring-derived planned amounts [ObservePeriodOverview] assembles. */
class ObservePeriodOverviewEnvelopesTest : ObservePeriodOverviewTestBase() {
    private val billsCategory = BuiltInCategories.RECURRING.id

    @Test
    fun noEnvelopesOrData_hasNoEnvelopeRows() =
        runTest {
            assertTrue(overview(Period.CurrentMonth).envelopes.envelopes.isEmpty())
        }

    @Test
    fun groups_sumPlannedFromRecurring_andSpend() =
        runTest {
            val food = groups.addEnvelope("Food").getOrThrow()
            val bills = groups.addEnvelope("Bills").getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)
            categories.setGroup(BuiltInCategories.RECURRING.id, bills)
            recurring.saveRecurring(recurringItem("f1", amountCents = 16_000L, categoryId = BuiltInCategories.FOOD.id))
            recurring.saveRecurring(recurringItem("r1", amountCents = 5_000L, categoryId = billsCategory))
            recurring.saveRecurring(recurringItem("r2", amountCents = 3_650L, categoryId = billsCategory))
            expenses.delegate.addExpense(testExpense("e1", 4_000L, noonOn(october, 5), BuiltInCategories.FOOD))

            val result = overview(Period.CurrentMonth)

            val rows = result.envelopes.envelopes
            assertEquals(listOf("Food", "Bills"), rows.map { it.envelope.name })
            assertEquals(16_000L, rows[0].amountCents)
            assertEquals(4_000L, rows[0].spentCents)
            assertEquals(8_650L, rows[1].amountCents)
            assertEquals(3, result.recurring.size)
            val row = result.budgets.single { it.category.id == BuiltInCategories.RECURRING.id }
            assertEquals(PlannedSource.RECURRING, row.plannedSource)
        }

    @Test
    fun unenvelopedSpend_appearsInUnenveloped() =
        runTest {
            groups.addEnvelope("Food")
            expenses.delegate.addExpense(testExpense("e1", 700L, noonOn(october, 5), BuiltInCategories.SHOPPING))

            val result = overview(Period.CurrentMonth).envelopes.unenveloped

            assertEquals(listOf(BuiltInCategories.SHOPPING), result.map { it.category })
            assertEquals(700L, result.single().spentCents)
        }

    @Test
    fun envelopeSpendAndRecurringPlanned_flowIntoTheOverview() =
        runTest {
            val food = groups.addEnvelope("Food").getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, food)
            recurring.saveRecurring(recurringItem("f1", amountCents = 20_000L, categoryId = BuiltInCategories.FOOD.id))
            expenses.delegate.addExpense(testExpense("e1", 25_000L, noonOn(october, 5), BuiltInCategories.FOOD))

            val progress = overview(Period.CurrentMonth).envelopes.envelopes.single()

            assertEquals(20_000L, progress.amountCents)
            assertEquals(25_000L, progress.spentCents)
            assertTrue(progress.isOver)
        }

    @Test
    fun envelopesFlowError_propagates() =
        runTest {
            val error = IllegalStateException("groups down")
            groups.nextObserveEnvelopesError = error

            assertFailsWithError(error) { overview(Period.CurrentMonth) }
        }

    @Test
    fun recurringFlowError_propagates() =
        runTest {
            val error = IllegalStateException("recurring down")
            recurring.nextObserveError = error

            assertFailsWithError(error) { overview(Period.CurrentMonth) }
        }
}
