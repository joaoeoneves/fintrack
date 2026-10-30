package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The 5.7 bulk members of [FakeExpenseRepository]: ids kept, all-or-nothing updates, linked refunds. */
class FakeExpenseRepositoryBulkTest {
    private val at = Instant.parse("2026-01-01T00:00:00Z")

    private fun expense(
        id: String,
        refundOf: String? = null,
        isRefund: Boolean = refundOf != null,
    ) = Expense(id, id, 100L, BuiltInCategories.FOOD, at, null, isRefund = isRefund, refundOf = refundOf)

    @Test
    fun addKeepingIds_keepsGivenIds_generatesBlankOnes_andReplacesSameId() =
        runBlocking {
            val repo = FakeExpenseRepository(listOf(expense("a")))

            val batch = listOf(expense("a").copy(amountCents = 5L), expense("b"), expense(""))

            val ids = repo.addExpensesKeepingIds(batch).getOrThrow()

            assertEquals(listOf("a", "b"), ids.take(2))
            assertTrue(ids[2].isNotBlank())
            val stored = repo.getAllExpenses().getOrThrow()
            assertEquals(4 - 1, stored.size)
            assertEquals(5L, stored.single { it.id == "a" }.amountCents)
        }

    @Test
    fun updateExpenses_isAllOrNothing_whenOneIsMissing() =
        runBlocking {
            val repo = FakeExpenseRepository(listOf(expense("a"), expense("b")))

            val result = repo.updateExpenses(listOf(expense("a").copy(amountCents = 9L), expense("ghost")))

            assertTrue(result.isFailure)
            assertEquals(listOf(100L, 100L), repo.getAllExpenses().getOrThrow().map { it.amountCents })
        }

    @Test
    fun deleteExpenses_removesTheGivenIds_andIgnoresMissingOnes() =
        runBlocking {
            val repo = FakeExpenseRepository(listOf(expense("a"), expense("b"), expense("c")))

            assertTrue(repo.deleteExpenses(listOf("a", "zzz", "c")).isSuccess)

            assertEquals(listOf("b"), repo.getAllExpenses().getOrThrow().map { it.id })
        }

    @Test
    fun observeLinkedRefunds_emitsOnlyRefundsWithALink_andUpdatesLive() =
        runBlocking {
            val seed = listOf(expense("o"), expense("r1", refundOf = "o"), expense("r2", isRefund = true))
            val repo = FakeExpenseRepository(seed)

            assertEquals(listOf("r1"), repo.observeLinkedRefunds().first().map { it.id })

            repo.addExpense(expense("r3", refundOf = "gone"))
            assertEquals(listOf("r1", "r3"), repo.observeLinkedRefunds().first().map { it.id })
        }
}
