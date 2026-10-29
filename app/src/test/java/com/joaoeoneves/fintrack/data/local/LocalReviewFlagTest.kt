package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.Income
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** The 5.4 review flag in the guest store: record mapping, the JSON shape and the two repositories' inbox calls. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalReviewFlagTest : LocalStoreTestBase() {
    private val at = "2026-03-10T12:00:00Z"
    private val expenses by lazy { LocalExpenseRepository(newStore(), SequenceIds()) }
    private val income by lazy { LocalIncomeRepository(newStore(), SequenceIds("inc")) }

    @Test
    fun expense_flagRoundTripsThroughTheMapping() {
        val raw = LocalExpense("e", "Rent", 500, "FOOD", at, needsReview = true)

        val domain = requireNotNull(raw.toDomainOrNull())

        assertTrue(domain.needsReview)
        assertEquals(raw, domain.toLocal())
        assertFalse(requireNotNull(LocalExpense("e", "Rent", 500, "FOOD", at).toDomainOrNull()).needsReview)
    }

    @Test
    fun income_flagRoundTripsThroughTheMapping() {
        val raw = LocalIncome("i", "Pay", 500, at, needsReview = true)

        val domain = requireNotNull(raw.toDomainOrNull())

        assertTrue(domain.needsReview)
        assertEquals(raw, domain.toLocal())
    }

    @Test
    fun oldJsonWithoutTheKey_readsAsReviewed() {
        val e =
            json.decodeFromString<LocalExpense>(
                """{"id":"e","name":"n","amountCents":5,"categoryId":"FOOD","date":"$at"}""",
            )
        val i = json.decodeFromString<LocalIncome>("""{"id":"i","source":"s","amountCents":5,"date":"$at"}""")

        assertFalse(requireNotNull(e.toDomainOrNull()).needsReview)
        assertFalse(requireNotNull(i.toDomainOrNull()).needsReview)
    }

    @Test
    fun documentWithAFlaggedExpense_roundTripsThroughJson() {
        val doc = LocalDataDocument(expenses = listOf(LocalExpense("e", "n", 5, "FOOD", at, needsReview = true)))

        assertEquals(doc, decode(json.encodeToString(doc)))
    }

    @Test
    fun expenses_observeNeedingReview_onlyFlagged_oldestFirst_andMarkReviewedClearsOnlyTheGivenIds() =
        runStoreTest {
            expenses.addExpense(expense("a", date = Instant.parse("2026-03-12T10:00:00Z")).copy(needsReview = true))
            expenses.addExpense(expense("b", date = Instant.parse("2026-03-11T10:00:00Z")).copy(needsReview = true))
            expenses.addExpense(expense("c").copy(needsReview = true))
            expenses.addExpense(expense("d"))

            assertEquals(listOf("c", "b", "a"), expenses.observeNeedingReview().first().map { it.id })

            assertTrue(expenses.markReviewed(listOf("b", "d", "ghost")).isSuccess)

            assertEquals(listOf("c", "a"), expenses.observeNeedingReview().first().map { it.id })
            assertFalse(expenses.getExpense("b").getOrThrow()!!.needsReview)
        }

    @Test
    fun incomes_observeNeedingReview_andMarkReviewed() =
        runStoreTest {
            income.addIncome(Income("a", "A", 10, Instant.parse("2026-03-12T10:00:00Z"), null, needsReview = true))
            income.addIncome(Income("b", "B", 10, Instant.parse("2026-03-11T10:00:00Z"), null, needsReview = true))
            income.addIncome(Income("c", "C", 10, Instant.parse("2026-03-10T10:00:00Z"), null))

            assertEquals(listOf("b", "a"), income.observeNeedingReview().first().map { it.id })

            income.markReviewed(listOf("a"))

            assertEquals(listOf("b"), income.observeNeedingReview().first().map { it.id })
            assertEquals(false, income.getIncome("a").getOrThrow()!!.needsReview)
        }

    @Test
    fun markReviewed_emptyInbox_orEmptyIds_isASuccessNoOp() =
        runStoreTest {
            assertTrue(expenses.markReviewed(emptyList()).isSuccess)
            assertTrue(income.markReviewed(listOf("x")).isSuccess)
            assertTrue(expenses.observeNeedingReview().first().isEmpty())
        }
}
