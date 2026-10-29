package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.data.planTimingFields
import com.joaoeoneves.fintrack.domain.model.AmountVersion
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_VERSIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

class StartMonthUpgradeTest {
    private val now = YearMonth.of(2026, 10)
    private val utc = ZoneOffset.UTC

    private fun expense(date: String) = LocalExpense("e$date", "x", 100, "FOOD", date)

    private fun income(date: String) = LocalIncome("i$date", "src", 100, date)

    private fun doc(
        expenses: List<LocalExpense> = emptyList(),
        income: List<LocalIncome> = emptyList(),
    ) = LocalDataDocument(
        schemaVersion = 18,
        expenses = expenses,
        income = income,
        budgets = listOf(LocalBudget("FOOD", 500)),
        plannedIncome = listOf(LocalPlannedIncome("p", "Pay", 1_000, 0)),
        categoryGroups = listOf(LocalCategoryGroup("g", "Group", 0)),
    )

    @Test
    fun upgrade_usesTheEarliestMonthOfExpensesAndIncome() {
        val upgraded =
            doc(
                expenses = listOf(expense("2026-05-10T12:00:00Z")),
                income = listOf(income("2026-03-01T12:00:00Z"), income("2026-08-01T12:00:00Z")),
            ).upgradedToStartMonths(now, utc)
        assertEquals("2026-03", upgraded.budgets.single().startMonth)
        assertEquals("2026-03", upgraded.plannedIncome.single().startMonth)
        assertEquals("2026-03", upgraded.categoryGroups.single().startMonth)
        assertEquals(19, upgraded.schemaVersion)
    }

    @Test
    fun upgrade_emptyFile_pinsNow() {
        val upgraded = doc().upgradedToStartMonths(now, utc)
        assertEquals("2026-10", upgraded.budgets.single().startMonth)
        assertEquals(19, upgraded.schemaVersion)
    }

    @Test
    fun upgrade_keepsExistingStartMonths_andIsIdempotent() {
        val base = doc(expenses = listOf(expense("2026-01-10T12:00:00Z")))
        val kept = base.copy(budgets = listOf(LocalBudget("FOOD", 500, startMonth = "2026-06")))
        val once = kept.upgradedToStartMonths(now, utc)
        assertEquals("2026-06", once.budgets.single().startMonth)
        assertEquals("2026-01", once.plannedIncome.single().startMonth)
        assertEquals(once, once.upgradedToStartMonths(now, utc))
    }

    @Test
    fun validatePlanTiming_acceptsNullAndWellFormed() {
        validatePlanTiming(null, emptyList())
        validatePlanTiming("2026-01", listOf(LocalAmountVersion("2026-02", null), LocalAmountVersion("2026-03", 0)))
        validatePlanTiming("2026-01", listOf(LocalAmountVersion("2026-02", MAX_AMOUNT_CENTS)))
    }

    @Test
    fun validatePlanTiming_rejectsBadShapes() {
        assertThrows(IllegalArgumentException::class.java) { validatePlanTiming("2026-1", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { validatePlanTiming("26-01", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            validatePlanTiming(null, listOf(LocalAmountVersion("2026/02", 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validatePlanTiming(null, listOf(LocalAmountVersion("2026-02", -1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validatePlanTiming(null, listOf(LocalAmountVersion("2026-02", MAX_AMOUNT_CENTS + 1)))
        }
        val tooMany = (1..MAX_AMOUNT_VERSIONS + 1).map { LocalAmountVersion("2026-%02d".format(it % 12 + 1), 1) }
        assertThrows(IllegalArgumentException::class.java) { validatePlanTiming(null, tooMany) }
    }

    @Test
    fun validateBudget_checksLimitAndTiming() {
        val known = setOf("FOOD")
        validateBudget(LocalBudget("FOOD", 1, startMonth = "2026-01"), known)
        assertThrows(IllegalArgumentException::class.java) { validateBudget(LocalBudget("FOOD", 0), known) }
        assertThrows(IllegalArgumentException::class.java) { validateBudget(LocalBudget("NOPE", 5), known) }
        assertThrows(IllegalArgumentException::class.java) {
            validateBudget(LocalBudget("FOOD", 5, startMonth = "bad"), known)
        }
    }

    @Test
    fun versionMapping_dropsUnparsableMonths_andRoundTrips() {
        val local =
            listOf(LocalAmountVersion("2026-02", 5), LocalAmountVersion("nope", 1), LocalAmountVersion("2026-03"))
        val domain = local.toDomainVersions()
        val expected = listOf(AmountVersion(YearMonth.of(2026, 2), 5), AmountVersion(YearMonth.of(2026, 3), null))
        assertEquals(expected, domain)
        assertEquals(listOf(local[0], local[2]), domain.toLocalVersions())
        assertNull("bad".toYearMonthOrNull())
        assertEquals(YearMonth.of(2026, 2), "2026-02".toYearMonthOrNull())
    }

    @Test
    fun firestoreFields_omittedWhenNullOrEmpty_keptOtherwise() {
        assertTrue(planTimingFields(null, emptyList()).isEmpty())
        val fields = planTimingFields(YearMonth.of(2026, 2), listOf(AmountVersion(YearMonth.of(2026, 3), null)))
        assertEquals("2026-02", fields["startMonth"])
        assertEquals(listOf(mapOf("from" to "2026-03", "amountCents" to null)), fields["versions"])
    }
}
