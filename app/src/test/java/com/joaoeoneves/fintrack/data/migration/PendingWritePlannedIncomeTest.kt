package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PendingWritePlannedIncomeTest {
    private fun plan(
        plannedIncome: List<com.joaoeoneves.fintrack.data.local.LocalPlannedIncome>,
        expenses: List<com.joaoeoneves.fintrack.data.local.LocalExpense> = emptyList(),
        income: List<com.joaoeoneves.fintrack.data.local.LocalIncome> = emptyList(),
    ) = MigrationPlan(emptyList(), emptyList(), expenses, income, plannedIncome, 0)

    @Test
    fun plannedIncome_isBatchedLast() {
        val batches =
            plan(
                plannedIncome = listOf(localPlanned("p1")),
                expenses = listOf(localExpense("e1")),
                income = listOf(localIncome("i1")),
            ).toBatches()

        assertEquals(listOf("expenses", "income", "plannedIncome"), batches.map { it.single().collection })
    }

    @Test
    fun plannedIncomeWrite_hasExpectedKeysAndValues() {
        val write = plan(listOf(localPlanned("p1", 1234, "Pay", 2))).toBatches().single().single()

        assertEquals("plannedIncome", write.collection)
        assertEquals("p1", write.id)
        assertEquals(setOf("label", "amountCents", "order", "updatedAt"), write.data.keys)
        assertEquals("Pay", write.data["label"])
        assertEquals(1234L, write.data["amountCents"])
        assertEquals(2, write.data["order"])
    }

    @Test
    fun plannedIncomeBatches_capAt400() {
        val batches = plan((0 until 401).map { localPlanned("p$it") }).toBatches()

        assertEquals(listOf(400, 1), batches.map { it.size })
        assertTrue(batches.all { b -> b.all { it.collection == "plannedIncome" } })
    }
}
