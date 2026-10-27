package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalExpenseRepositoryValidationTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalExpenseRepository(store, SequenceIds()) }

    private suspend fun assertRejected(e: com.joaoeoneves.fintrack.domain.model.Expense) {
        val before = store.data.first()
        val result = repo.addExpense(e)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(before, store.data.first())
    }

    @Test
    fun blankName_rejected() = runStoreTest { assertRejected(expense(name = "")) }

    @Test
    fun name200Accepted_201Rejected_codePointsNotChars() =
        runStoreTest {
            assertTrue(repo.addExpense(expense("a", name = "😀".repeat(200))).isSuccess)
            assertRejected(expense("b", name = "😀".repeat(201)))
            assertRejected(expense("c", name = "x".repeat(201)))
        }

    @Test
    fun zeroAndNegativeAmounts_rejected() =
        runStoreTest {
            assertRejected(expense(amountCents = 0))
            assertRejected(expense(amountCents = -100))
        }

    @Test
    fun note2000Accepted_2001Rejected() =
        runStoreTest {
            assertTrue(repo.addExpense(expense("a").copy(note = "n".repeat(2000))).isSuccess)
            assertRejected(expense("b").copy(note = "n".repeat(2001)))
        }

    @Test
    fun unknownCategory_rejected_butStoredCustomCategoryAccepted() =
        runStoreTest {
            assertRejected(expense(categoryId = "custom1"))
            store.seedCategory("custom1")
            assertTrue(repo.addExpense(expense("ok", categoryId = "custom1")).isSuccess)
        }

    @Test
    fun bulkAdd_assignsNewIds_singleWrite() =
        runStoreTest {
            val result = repo.addExpenses(listOf(expense("keep1"), expense("keep2"), expense("keep3")))
            assertEquals(BulkAddResult(3, null), result)
            assertEquals(listOf("id1", "id2", "id3"), diskDoc().expenses.map { it.id })
            assertEquals(1L, diskDoc().revision)
        }

    @Test
    fun bulkAdd_oneInvalid_writesNothing() =
        runStoreTest {
            val result = repo.addExpenses(listOf(expense("a"), expense("b", amountCents = 0), expense("c")))
            assertEquals(0, result.succeededCount)
            assertTrue(result.failure is IllegalArgumentException)
            assertFalse(dataFile.exists())
        }

    @Test
    fun bulkAdd_empty_isSuccessWithZeroAndNoWrite() =
        runStoreTest {
            assertEquals(BulkAddResult(0, null), repo.addExpenses(emptyList()))
            assertFalse(dataFile.exists())
            assertNull(repo.addExpenses(emptyList()).failure)
        }
}
