package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.data.FakeSalaryLineLabelsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class RelabelPaydayIncomesTest {
    private val en = FakeSalaryLineLabelsProvider.ENGLISH
    private val pt = FakeSalaryLineLabelsProvider.PORTUGUESE
    private val defaults = FakeSalaryLineLabelsProvider().knownDefaults()
    private val oct = YearMonth.of(2026, 10)

    private fun income(
        id: String,
        source: String,
    ) = Income(id, source, 1_000, Instant.parse("2026-10-30T12:00:00Z"), null)

    private fun relabel(vararg incomes: Income) = relabelPaydayIncomes(incomes.toList(), pt, defaults)

    @Test
    fun englishDefaultSource_isRenamedToPortuguese() {
        val stored = income(paydayIncomeId(MANAGED_NET_SALARY, oct), "Net salary")

        assertEquals(listOf(stored.copy(source = "Salário líquido")), relabel(stored))
    }

    @Test
    fun holidayAndChristmasPaydays_areRenamedToTheirOwnLabels() {
        val holiday = income("pay_profile_holiday_202610", "Holiday subsidy")
        val christmas = income("pay_profile_christmas_202610", "Christmas subsidy")

        assertEquals(
            listOf("Subsídio de férias", "Subsídio de Natal"),
            relabel(holiday, christmas).map { it.source },
        )
    }

    @Test
    fun legacySharedSubsidySource_isRenamedPerLine() {
        val holiday = income(paydayIncomeId(MANAGED_HOLIDAY, oct), "Subsidy")
        val christmas = income(paydayIncomeId(MANAGED_CHRISTMAS, oct), "Subsídio")

        assertEquals(
            listOf("Subsídio de férias", "Subsídio de Natal"),
            relabel(holiday, christmas).map { it.source },
        )
    }

    @Test
    fun alreadyInCurrentLanguage_isNotWritten() {
        assertTrue(relabel(income(paydayIncomeId(MANAGED_NET_SALARY, oct), "Salário líquido")).isEmpty())
    }

    @Test
    fun userEditedSource_isUntouched() {
        assertTrue(relabel(income(paydayIncomeId(MANAGED_NET_SALARY, oct), "My wage")).isEmpty())
    }

    @Test
    fun defaultOfAnotherLine_isUntouched() {
        // "Meal allowance" is a default, but not the one of the net salary line.
        assertTrue(relabel(income(paydayIncomeId(MANAGED_NET_SALARY, oct), "Meal allowance")).isEmpty())
    }

    @Test
    fun manualLineAndPlainIncome_areUntouched() {
        val manual = income(paydayIncomeId("manual", oct), "Net salary")
        val plain = income("some-id", "Net salary")
        val malformed = income("pay_202610", "Net salary")

        assertTrue(relabel(manual, plain, malformed).isEmpty())
    }

    @Test
    fun extraLine_isUntouched() {
        val key = managedExtraKey("bonus", 10)

        assertTrue(relabel(income(paydayIncomeId(key, oct), "Bonus")).isEmpty())
    }

    @Test
    fun isIdempotent() {
        val stored = listOf(income(paydayIncomeId(MANAGED_MEAL, oct), "Meal allowance"))

        val once = relabelPaydayIncomes(stored, pt, defaults)
        assertEquals(1, once.size)
        assertTrue(relabelPaydayIncomes(once, pt, defaults).isEmpty())
    }

    @Test
    fun emptyInput_givesNothing() {
        assertTrue(relabelPaydayIncomes(emptyList(), pt, defaults).isEmpty())
    }
}
