package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LocalRecurringRepository.confirmOccurrence]: one atomic store update for the transaction plus the cursor. */
class LocalRecurringRepositoryConfirmTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalRecurringRepository(store, SequenceIds("r")) }
    private val march15 = day(2026, 3, 15)

    private suspend fun seed(vararg ids: String) = ids.forEach { repo.saveRecurring(recurringItem(id = it)) }

    @Test
    fun confirm_expense_storesItAndMovesTheCursorInOneRevisionBump() =
        runStoreTest {
            seed("r1")
            val before = store.data.first().revision

            val id = repo.confirmOccurrence("r1", march15, expense(id = "rec_r1_20260315")).getOrThrow()

            val doc = store.data.first()
            assertEquals("rec_r1_20260315", id)
            assertEquals(before + 1, doc.revision)
            assertEquals(listOf("rec_r1_20260315"), doc.expenses.map { it.id })
            assertEquals("2026-03-15", doc.recurring.single().handledThrough)
        }

    @Test
    fun confirm_income_storesAnIncome() =
        runStoreTest {
            repo.saveRecurring(recurringItem(id = "s", type = RecurringType.INCOME))

            assertTrue(repo.confirmOccurrence("s", march15, income(id = "rec_s_20260315")).isSuccess)

            val doc = store.data.first()
            assertEquals(listOf("rec_s_20260315"), doc.income.map { it.id })
            assertTrue(doc.expenses.isEmpty())
        }

    @Test
    fun reConfirm_sameOccurrence_doesNotDuplicate() =
        runStoreTest {
            seed("r1")
            val tx = expense(id = "rec_r1_20260315")

            repo.confirmOccurrence("r1", march15, tx)
            assertTrue(repo.confirmOccurrence("r1", march15, tx).isSuccess)

            val doc = store.data.first()
            assertEquals(1, doc.expenses.size)
            assertEquals("2026-03-15", doc.recurring.single().handledThrough)
        }

    @Test
    fun invalidTransaction_leavesTheTransactionsAndTheCursorUnchanged() =
        runStoreTest {
            seed("r1")
            val before = store.data.first()

            val result = repo.confirmOccurrence("r1", march15, expense(id = "rec_r1_20260315", amountCents = 0))

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, store.data.first())
            assertEquals(before, diskDoc())
        }

    @Test
    fun missingItem_fails_andStoresNoTransaction() =
        runStoreTest {
            seed("r1")
            val before = store.data.first()

            assertTrue(repo.confirmOccurrence("nope", march15, expense(id = "rec_nope_20260315")).isFailure)

            assertEquals(before, store.data.first())
        }

    @Test
    fun confirm_unknownCategoryOnTheExpense_isRejectedAtomically() =
        runStoreTest {
            seed("r1")
            val before = store.data.first()

            assertTrue(repo.confirmOccurrence("r1", march15, expense(id = "x", categoryId = "gone")).isFailure)

            assertEquals(before, store.data.first())
        }
}
