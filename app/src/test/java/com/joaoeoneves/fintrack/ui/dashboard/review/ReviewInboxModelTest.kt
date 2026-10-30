package com.joaoeoneves.fintrack.ui.dashboard.review

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.RecurringCandidate
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.ui.dashboard.recurring.RecurringSuggestionModel
import com.joaoeoneves.fintrack.ui.dashboard.recurring.RecurringSuggestionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class ReviewInboxModelTest {
    private val candidate =
        RecurringCandidate("netflix", "Netflix", 1_000L, BuiltInCategories.FOOD.id, 5, YearMonth.of(2026, 10))

    private fun reviewVisible(total: Int) =
        ReviewInboxUiState.Visible(
            items = listOf(testExpense("a", 100L, Instant.EPOCH)),
            total = total,
        )

    private fun model(
        state: ReviewInboxUiState,
        suggestion: RecurringSuggestionUiState,
    ) = ReviewInboxModel(
        state = state,
        suggestion = RecurringSuggestionModel(suggestion, {}, {}),
        onConfirmAll = {},
        onRemove = {},
        onOpenExpense = {},
        onOpenIncome = {},
    )

    @Test
    fun nothingToReviewAndNoSuggestion_hasNoContent() {
        val m = model(ReviewInboxUiState.Hidden, RecurringSuggestionUiState.Hidden)
        assertFalse(m.hasContent)
        assertEquals(0, m.count)
    }

    @Test
    fun onlyRecords_countsTheFullTotal() {
        val m = model(reviewVisible(7), RecurringSuggestionUiState.Hidden)
        assertTrue(m.hasContent)
        assertEquals(7, m.count)
    }

    @Test
    fun onlyASuggestion_showsTheCardWithCountOne() {
        val m = model(ReviewInboxUiState.Hidden, RecurringSuggestionUiState.Visible(candidate))
        assertTrue(m.hasContent)
        assertEquals(1, m.count)
    }

    @Test
    fun recordsAndSuggestion_countsBoth() {
        val m = model(reviewVisible(3), RecurringSuggestionUiState.Visible(candidate))
        assertTrue(m.hasContent)
        assertEquals(4, m.count)
    }
}
