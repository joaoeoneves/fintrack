package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class IncomeMigrationTest {
    private val today = day(2026, 10, 28)
    private val sep = YearMonth.of(2026, 9)

    @Suppress("LongParameterList")
    private fun income(
        id: String = "i1",
        name: String = "Rent received",
        cents: Long = 40_000,
        frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
        dayOfMonth: Int = 5,
        autoPost: Boolean = false,
        active: Boolean = true,
        endDate: java.time.LocalDate? = null,
        handledThrough: java.time.LocalDate? = null,
    ) = recurringItem(
        id = id,
        type = RecurringType.INCOME,
        name = name,
        amountCents = cents,
        frequency = frequency,
        dayOfMonth = dayOfMonth,
        autoPost = autoPost,
        active = active,
        endDate = endDate,
        handledThrough = handledThrough,
    )

    private fun line(
        id: String,
        order: Int = 0,
        autoRecord: Boolean = false,
    ) = PlannedIncome(id, id, 1_000, order, autoRecord = autoRecord)

    private fun migrate(
        recurring: List<RecurringItem> = emptyList(),
        lines: List<PlannedIncome> = emptyList(),
        settings: AutomationSettings = AutomationSettings(),
    ) = incomeMigrationOf(recurring, lines, settings, today)

    @Test
    fun nothingToDo_isEmpty() {
        assertTrue(migrate().isEmpty)
        assertTrue(migrate(recurring = listOf(recurringItem())).isEmpty)
    }

    @Test
    fun monthlyItem_becomesAFixedMonthlyLine_paidOnItsDay() {
        val result = migrate(listOf(income(name = "  Rent received  ", dayOfMonth = 5)), listOf(line("a", order = 3)))

        val created = result.newLines.single()
        assertEquals("rinc_i1", created.id)
        assertEquals("Rent received", created.label)
        assertEquals(40_000L, created.amountCents)
        assertEquals(IncomeBasis.FIXED, created.basis)
        assertEquals(IncomeFrequency.MONTHLY, created.frequency)
        assertEquals(null, created.monthOfYear)
        assertEquals(5, created.payDay)
        assertFalse(created.autoRecord)
        assertEquals(4, created.order)
        assertEquals(listOf("i1"), result.deleteRecurringIds)
        assertTrue(result.cursors.isEmpty())
    }

    @Test
    fun yearlyItem_becomesAYearlyLine_withItsMonth() {
        val item = income(frequency = RecurringFrequency.YEARLY).copy(monthOfYear = 7)

        val created = migrate(listOf(item)).newLines.single()

        assertEquals(IncomeFrequency.YEARLY, created.frequency)
        assertEquals(7, created.monthOfYear)
    }

    @Test
    fun longName_isCappedToTheLabelLimit() {
        val created = migrate(listOf(income(name = "x".repeat(MAX_PLAN_LABEL_LENGTH + 20)))).newLines.single()

        assertEquals(MAX_PLAN_LABEL_LENGTH, created.label.length)
    }

    @Test
    fun autoPost_becomesAutoRecord_withCursorFromHandledThrough() {
        val item = income(autoPost = true, handledThrough = day(2026, 8, 5))

        val result = migrate(listOf(item))

        assertTrue(result.newLines.single().autoRecord)
        assertEquals(mapOf("rinc_i1" to YearMonth.of(2026, 8)), result.cursors)
    }

    @Test
    fun autoPost_withoutHandledThrough_usesTheInitialCursor() {
        val result = migrate(listOf(income(autoPost = true, dayOfMonth = 5)))

        // Day 5 of October has passed on the 28th.
        assertEquals(mapOf("rinc_i1" to YearMonth.of(2026, 10)), result.cursors)
    }

    @Test
    fun existingCursor_isNotOverwritten() {
        val settings = AutomationSettings(lineRecordedThrough = mapOf("rinc_i1" to sep))
        val existing = line("rinc_i1", autoRecord = true)

        val result = migrate(listOf(income(autoPost = true)), listOf(existing), settings)

        assertTrue(result.cursors.isEmpty())
    }

    @Test
    fun inactiveOrEndedItems_getNoLine_butAreDeleted() {
        val paused = income("paused", active = false)
        val ended = income("ended", endDate = day(2026, 6, 30))

        val result = migrate(listOf(paused, ended))

        assertTrue(result.newLines.isEmpty())
        assertEquals(listOf("ended", "paused"), result.deleteRecurringIds)
    }

    @Test
    fun existingRincLine_isNotOverwritten_butItemIsStillDeleted() {
        val existing = line("rinc_i1").copy(label = "Edited", amountCents = 7)

        val result = migrate(listOf(income()), listOf(existing))

        assertTrue(result.newLines.isEmpty())
        assertEquals(listOf("i1"), result.deleteRecurringIds)
    }

    @Test
    fun expenseItems_areIgnored() {
        assertTrue(migrate(listOf(recurringItem(type = RecurringType.EXPENSE, autoPost = true))).isEmpty)
    }

    @Test
    fun legacyPaydayEnabled_turnsAutoRecordOnForExistingLines_withTheLegacyCursor() {
        val settings = AutomationSettings(legacyPaydayEnabled = true, legacyPaydayRecordedThrough = sep)
        val lines = listOf(line("a"), line("b", autoRecord = true))

        val result = migrate(lines = lines, settings = settings)

        assertEquals(setOf("a"), result.autoRecordLineIds)
        assertEquals(mapOf("a" to sep), result.cursors)
        assertTrue(result.clearLegacyPayday)
    }

    @Test
    fun legacyPaydayEnabled_withoutLegacyCursor_usesTheInitialCursor() {
        val result = migrate(lines = listOf(line("a")), settings = AutomationSettings(legacyPaydayEnabled = true))

        assertEquals(mapOf("a" to YearMonth.of(2026, 9)), result.cursors)
    }

    @Test
    fun legacyPaydayEnabled_withNoLines_doesNothing() {
        val result = migrate(settings = AutomationSettings(legacyPaydayEnabled = true))

        assertTrue(result.isEmpty)
        assertFalse(result.clearLegacyPayday)
    }

    @Test
    fun legacyPaydayDisabled_leavesLinesAlone() {
        assertTrue(migrate(lines = listOf(line("a"))).isEmpty)
    }

    @Test
    fun isIdempotent_onceApplied() {
        val item = income(autoPost = true, handledThrough = day(2026, 8, 5))
        val first = migrate(listOf(item), listOf(line("x")), AutomationSettings(legacyPaydayEnabled = true))
        val lines = listOf(line("x", autoRecord = true)) + first.newLines
        val settings = AutomationSettings(lineRecordedThrough = first.cursors)

        val second = migrate(emptyList(), lines, settings)

        assertTrue(second.isEmpty)
    }
}

class LooksLikeSalaryTest {
    private val net = PlannedIncome("n", "Net salary", 100_000, 0, managedKey = MANAGED_NET_SALARY)

    private fun manual(
        label: String = "Side gig",
        cents: Long = 30_000,
        frequency: IncomeFrequency = IncomeFrequency.MONTHLY,
    ) = PlannedIncome(
        "m",
        label,
        cents,
        1,
        frequency = frequency,
        monthOfYear = 3.takeIf { frequency == IncomeFrequency.YEARLY },
    )

    @Test
    fun keywords_matchIgnoringCaseAndAccents() {
        for (label in listOf("Salário", "ORDENADO", "Vencimento Abril", "my Salary", "WAGE", "Paycheck")) {
            assertTrue(label, manual(label).looksLikeSalary(listOf(net)))
        }
    }

    @Test
    fun amountWithinTenPercent_matches_justOutsideDoesNot() {
        assertTrue(manual(cents = 90_000).looksLikeSalary(listOf(net)))
        assertTrue(manual(cents = 110_000).looksLikeSalary(listOf(net)))
        assertFalse(manual(cents = 89_999).looksLikeSalary(listOf(net)))
        assertFalse(manual(cents = 110_001).looksLikeSalary(listOf(net)))
    }

    @Test
    fun unrelatedLine_doesNotMatch() {
        assertFalse(manual().looksLikeSalary(listOf(net)))
    }

    @Test
    fun managedLines_neverMatch() {
        val managed = manual("Salary").copy(managedKey = MANAGED_MEAL)

        assertFalse(managed.looksLikeSalary(listOf(net)))
        assertFalse(net.looksLikeSalary(listOf(net)))
    }

    @Test
    fun nonMonthlyLines_neverMatch() {
        assertFalse(manual("Salary", 100_000, IncomeFrequency.YEARLY).looksLikeSalary(listOf(net)))
    }

    @Test
    fun noSalaryProfile_isFalse() {
        assertFalse(manual("Salary", 100_000).looksLikeSalary(emptyList()))
        assertFalse(manual("Salary", 100_000).looksLikeSalary(listOf(manual("Other"))))
    }
}
