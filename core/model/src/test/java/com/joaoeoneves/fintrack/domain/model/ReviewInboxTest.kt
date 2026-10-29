package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReviewInboxTest {
    private fun expense(
        id: String,
        date: String,
        review: Boolean = true,
    ) = Expense(id, "E$id", 100L, BuiltInCategories.FOOD, Instant.parse(date), null, needsReview = review)

    private fun income(
        id: String,
        date: String,
        review: Boolean = true,
    ) = Income(id, "I$id", 100L, Instant.parse(date), null, needsReview = review)

    @Test
    fun emptyInputs_giveAnEmptyInbox() = assertTrue(reviewInbox(emptyList(), emptyList()).isEmpty())

    @Test
    fun keepsOnlyFlaggedRecords() {
        val result =
            reviewInbox(
                listOf(expense("a", "2026-03-01T10:00:00Z"), expense("b", "2026-03-02T10:00:00Z", review = false)),
                listOf(income("c", "2026-03-03T10:00:00Z"), income("d", "2026-03-04T10:00:00Z", review = false)),
            )

        assertEquals(listOf("c", "a"), result.map { it.id })
    }

    @Test
    fun sortsNewestFirst_acrossExpensesAndIncomes() {
        val result =
            reviewInbox(
                listOf(expense("a", "2026-03-01T10:00:00Z"), expense("c", "2026-03-05T10:00:00Z")),
                listOf(income("b", "2026-03-03T10:00:00Z")),
            )

        assertEquals(listOf("c", "b", "a"), result.map { it.id })
    }

    @Test
    fun sameInstant_isBrokenById_acrossKinds() {
        val result =
            reviewInbox(
                listOf(expense("z", "2026-03-01T10:00:00Z"), expense("b", "2026-03-01T10:00:00Z")),
                listOf(income("a", "2026-03-01T10:00:00Z")),
            )

        assertEquals(listOf("a", "b", "z"), result.map { it.id })
    }
}
