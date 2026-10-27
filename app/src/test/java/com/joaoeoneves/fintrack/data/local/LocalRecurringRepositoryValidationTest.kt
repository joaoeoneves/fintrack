package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The write-time validation of [LocalRecurringRepository.saveRecurring] (mirrors firestore.rules). */
class LocalRecurringRepositoryValidationTest : LocalStoreTestBase() {
    private val store by lazy { newStore() }
    private val repo by lazy { LocalRecurringRepository(store, SequenceIds("r")) }

    private suspend fun assertRejected(item: RecurringItem) {
        val result = repo.saveRecurring(item)
        assertTrue("expected rejection of $item", result.exceptionOrNull() is IllegalArgumentException)
        assertFalse(dataFile.exists())
    }

    @Test
    fun name_200CodePointsAccepted_201Rejected_emojiCountOnce() =
        runStoreTest {
            assertTrue(repo.saveRecurring(recurringItem(id = "a", name = "a".repeat(200))).isSuccess)
            assertTrue(repo.saveRecurring(recurringItem(id = "b", name = "💰".repeat(200))).isSuccess)
            assertTrue(repo.saveRecurring(recurringItem(id = "c", name = "💰".repeat(201))).isFailure)
            assertTrue(repo.saveRecurring(recurringItem(id = "d", name = "a".repeat(201))).isFailure)
        }

    @Test
    fun emptyName_rejected() = runStoreTest { assertRejected(recurringItem(name = "")) }

    @Test
    fun amount_zeroAndNegativeRejected_oneCentAccepted() =
        runStoreTest {
            assertRejected(recurringItem(amountCents = 0))
            assertRejected(recurringItem(amountCents = -1))
            assertTrue(repo.saveRecurring(recurringItem(amountCents = 1)).isSuccess)
        }

    @Test
    fun category_expenseNeedsOne_incomeMustNotHaveOne() =
        runStoreTest {
            assertRejected(recurringItem(categoryId = null))
            assertRejected(recurringItem(type = RecurringType.INCOME, categoryId = CategoryId("FOOD")))
            assertTrue(repo.saveRecurring(recurringItem(type = RecurringType.INCOME)).isSuccess)
        }

    @Test
    fun category_unknownRejected_customKnownAccepted() =
        runStoreTest {
            assertRejected(recurringItem(categoryId = CategoryId("gym")))

            store.seedCategory("gym")

            assertTrue(repo.saveRecurring(recurringItem(categoryId = CategoryId("gym"))).isSuccess)
        }

    @Test
    fun endDateBeforeStartDate_rejected_sameDayAccepted() =
        runStoreTest {
            assertRejected(recurringItem(startDate = day(2026, 3, 1), endDate = day(2026, 2, 28)))
            val sameDay = recurringItem(startDate = day(2026, 3, 1), endDate = day(2026, 3, 1))
            assertTrue(repo.saveRecurring(sameDay).isSuccess)
        }

    @Test
    fun dayOfMonth_zeroAnd32Rejected_oneAnd31Accepted() =
        runStoreTest {
            assertRejected(recurringItem(dayOfMonth = 0))
            assertRejected(recurringItem(dayOfMonth = 32))
            assertTrue(repo.saveRecurring(recurringItem(id = "a", dayOfMonth = 1)).isSuccess)
            assertTrue(repo.saveRecurring(recurringItem(id = "b", dayOfMonth = 31)).isSuccess)
        }

    @Test
    fun monthOfYear_requiredForYearly_forbiddenForMonthly_rangeChecked() =
        runStoreTest {
            assertRejected(recurringItem(frequency = RecurringFrequency.YEARLY, monthOfYear = null))
            assertRejected(recurringItem(frequency = RecurringFrequency.YEARLY, monthOfYear = 13))
            assertRejected(recurringItem(frequency = RecurringFrequency.YEARLY, monthOfYear = 0))
            assertRejected(recurringItem(frequency = RecurringFrequency.MONTHLY, monthOfYear = 3))
            val december = recurringItem(frequency = RecurringFrequency.YEARLY, monthOfYear = 12)
            assertTrue(repo.saveRecurring(december).isSuccess)
        }

    @Test
    fun note_2000CodePointsAccepted_2001Rejected() =
        runStoreTest {
            assertTrue(repo.saveRecurring(recurringItem(id = "a", note = "n".repeat(2000))).isSuccess)
            assertTrue(repo.saveRecurring(recurringItem(id = "b", note = "n".repeat(2001))).isFailure)
        }
}
