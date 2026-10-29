package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class RecurringSuggestionTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 20)
    private var seq = 0

    @Suppress("LongParameterList")
    private fun expense(
        name: String,
        year: Int,
        month: Int,
        cents: Long = 10_000L,
        day: Int = 5,
        id: String = "e${seq++}",
        refund: Boolean = false,
        category: Category = BuiltInCategories.FOOD,
    ) = Expense(
        id,
        name,
        cents,
        category,
        Instant.parse("%04d-%02d-%02dT10:00:00Z".format(year, month, day)),
        null,
        isRefund = refund,
    )

    /** One record per month for April, May and June 2026. */
    private fun streak(
        name: String = "Netflix",
        cents: List<Long> = listOf(10_000L, 10_000L, 10_000L),
    ) = cents.mapIndexed { i, c -> expense(name, 2026, 4 + i, c) }

    private fun run(
        expenses: List<Expense>,
        recurring: List<RecurringItem> = emptyList(),
        dismissed: Set<String> = emptySet(),
        now: LocalDate = today,
    ) = recurringCandidates(expenses, recurring, dismissed, now, zone)

    @Test
    fun threeConsecutiveMonths_qualify_withDataFromTheLatestRecord() {
        val history =
            listOf(
                expense("netflix", 2026, 4, 9_900L, day = 3),
                expense("Netflix", 2026, 5, 10_000L, day = 4),
                expense("Netflix ", 2026, 6, 10_200L, day = 7, category = BuiltInCategories.SHOPPING),
            )

        assertEquals(
            listOf(
                RecurringCandidate(
                    "netflix",
                    "Netflix ",
                    10_200L,
                    BuiltInCategories.SHOPPING.id,
                    7,
                    YearMonth.of(2026, 6),
                ),
            ),
            run(history),
        )
    }

    @Test
    fun aGapMonth_doesNotQualify() {
        val history = listOf(expense("Netflix", 2026, 3), expense("Netflix", 2026, 5), expense("Netflix", 2026, 6))

        assertTrue(run(history).isEmpty())
    }

    @Test
    fun twoMonthsOnly_doNotQualify() {
        assertTrue(run(streak().drop(1)).isEmpty())
    }

    @Test
    fun twoRecordsInOneMonth_doNotQualify() {
        assertTrue(run(streak() + expense("Netflix", 2026, 5, day = 20)).isEmpty())
        assertTrue(run(streak() + expense("Netflix", 2026, 6, day = 21)).isEmpty())
    }

    @Test
    fun tenPercentDifference_passes_tenPointZeroOnePercent_fails() {
        // Reference is the latest amount, 10_000: 9_000 and 11_000 are exactly 10% away.
        assertEquals(1, run(streak(cents = listOf(9_000L, 11_000L, 10_000L))).size)
        assertTrue(run(streak(cents = listOf(8_999L, 10_000L, 10_000L))).isEmpty())
        assertTrue(run(streak(cents = listOf(10_000L, 11_001L, 10_000L))).isEmpty())
    }

    @Test
    fun existingRecurringExpense_blocks_activeOrNot_ignoringCaseAndAccents() {
        val history = streak("Café")

        assertTrue(run(history, listOf(recurringItem(name = "CAFE"))).isEmpty())
        assertTrue(run(history, listOf(recurringItem(name = " cafe ", active = false))).isEmpty())
    }

    @Test
    fun existingRecurringIncome_withTheSameName_doesNotBlock() {
        val income = recurringItem(type = RecurringType.INCOME, name = "Netflix")

        assertEquals(1, run(streak(), listOf(income)).size)
    }

    @Test
    fun dismissedKey_isExcluded_otherKeysAreNot() {
        assertTrue(run(streak(), dismissed = setOf("netflix")).isEmpty())
        assertEquals(1, run(streak(), dismissed = setOf("spotify")).size)
    }

    @Test
    fun latestMonthTooOld_isExcluded() {
        // The streak ends in June, but today is August: not the current nor the previous month.
        assertTrue(run(streak(), now = LocalDate.of(2026, 8, 1)).isEmpty())
    }

    @Test
    fun latestMonth_asPreviousMonth_works() {
        assertEquals(YearMonth.of(2026, 6), run(streak(), now = LocalDate.of(2026, 7, 2)).single().lastMonth)
    }

    @Test
    fun latestMonth_asCurrentMonth_works_acrossAYearBoundary() {
        val history = listOf(expense("Gym", 2025, 11), expense("Gym", 2025, 12), expense("Gym", 2026, 1))

        assertEquals(1, run(history, now = LocalDate.of(2026, 1, 31)).size)
        assertEquals(1, run(history, now = LocalDate.of(2026, 2, 1)).size)
        assertTrue(run(history, now = LocalDate.of(2026, 3, 1)).isEmpty())
    }

    @Test
    fun recEntries_refunds_andBlankNames_areIgnored() {
        val rec = streak().map { it.copy(id = "rec_r1_${it.id}") }
        val refunds = streak().map { it.copy(isRefund = true) }
        val blank = streak("")

        assertTrue(run(rec + refunds + blank).isEmpty())
    }

    @Test
    fun candidates_areSortedLatestFirst() {
        val older = (3..5).map { expense("Old", 2026, it, day = 25) }
        val newer = streak("New")

        assertEquals(listOf("New", "Old"), run(older + newer, now = LocalDate.of(2026, 6, 20)).map { it.name })
    }
}
