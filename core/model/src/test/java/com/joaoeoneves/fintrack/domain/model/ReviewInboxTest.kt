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

    private fun awaiting(
        id: String,
        date: String = "2026-03-01T10:00:00Z",
    ) = Expense(id, "E$id", 100L, BuiltInCategories.FOOD, Instant.parse(date), null, awaitingRefund = true)

    private fun refundOf(
        original: String?,
        id: String = "r-$original",
    ) = Expense(id, "R", 40L, BuiltInCategories.FOOD, Instant.parse("2026-03-05T10:00:00Z"), null)
        .copy(isRefund = true, refundOf = original)

    @Test
    fun awaitingWithoutARefund_isShown() {
        val result = reviewInbox(emptyList(), emptyList(), awaiting = listOf(awaiting("a")))

        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun awaitingWithAPartialRefund_isHidden_untilTheRefundIsDeleted() {
        val a = awaiting("a")

        assertTrue(reviewInbox(emptyList(), emptyList(), listOf(a), listOf(refundOf("a"))).isEmpty())
        assertEquals(listOf("a"), reviewInbox(emptyList(), emptyList(), listOf(a), emptyList()).map { it.id })
    }

    @Test
    fun refundOfAnotherExpense_doesNotHideTheAwaitingOne() {
        val result = reviewInbox(emptyList(), emptyList(), listOf(awaiting("a")), listOf(refundOf("b"), refundOf(null)))

        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun awaitingAlsoNeedingReview_appearsOnce() {
        val both = awaiting("a").copy(needsReview = true)

        val result = reviewInbox(listOf(both), emptyList(), awaiting = listOf(both))

        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun awaitingIsMergedAndSortedWithTheFlaggedRecords() {
        val result =
            reviewInbox(
                listOf(expense("x", "2026-03-02T10:00:00Z")),
                listOf(income("y", "2026-03-04T10:00:00Z")),
                awaiting = listOf(awaiting("a", "2026-03-03T10:00:00Z")),
            )

        assertEquals(listOf("y", "a", "x"), result.map { it.id })
    }

    @Test
    fun hiddenByRefund_butStillFlaggedForReview_staysViaNeedsReview() {
        val both = awaiting("a").copy(needsReview = true)

        val result = reviewInbox(listOf(both), emptyList(), listOf(both), listOf(refundOf("a")))

        assertEquals(listOf("a"), result.map { it.id })
    }
}
