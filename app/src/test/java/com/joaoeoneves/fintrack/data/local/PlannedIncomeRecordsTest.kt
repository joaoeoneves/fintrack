package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.YearMonth

/** The v2 planned-income record: validation mirroring `firestore.rules`, lenient mapping, old-shape JSON. */
class PlannedIncomeRecordsTest {
    @Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
    private fun record(
        basis: String = "FIXED",
        frequency: String = "MONTHLY",
        monthOfYear: Int? = null,
        yearMonth: String? = null,
        skipMonths: List<Int> = emptyList(),
        amount: Long = 1000,
        label: String = "Pay",
    ) = LocalPlannedIncome("p", label, amount, 0, basis, frequency, monthOfYear, yearMonth, skipMonths)

    private fun invalid(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    @Test
    fun monthly_valid_andRejectsMonthFields() {
        validatePlannedIncome(record())
        validatePlannedIncome(record(skipMonths = listOf(1, 12)))
        invalid { validatePlannedIncome(record(monthOfYear = 3)) }
        invalid { validatePlannedIncome(record(yearMonth = "2026-03")) }
    }

    @Test
    fun yearly_needsMonthOneToTwelve_andNoYearMonth() {
        validatePlannedIncome(record(frequency = "YEARLY", monthOfYear = 1))
        validatePlannedIncome(record(frequency = "YEARLY", monthOfYear = 12))
        invalid { validatePlannedIncome(record(frequency = "YEARLY")) }
        invalid { validatePlannedIncome(record(frequency = "YEARLY", monthOfYear = 0)) }
        invalid { validatePlannedIncome(record(frequency = "YEARLY", monthOfYear = 13)) }
        invalid { validatePlannedIncome(record(frequency = "YEARLY", monthOfYear = 5, yearMonth = "2026-05")) }
    }

    @Test
    fun once_needsValidYearMonth_andNoMonthOfYear() {
        validatePlannedIncome(record(frequency = "ONCE", yearMonth = "2026-12"))
        invalid { validatePlannedIncome(record(frequency = "ONCE")) }
        invalid { validatePlannedIncome(record(frequency = "ONCE", yearMonth = "2026-13")) }
        invalid { validatePlannedIncome(record(frequency = "ONCE", yearMonth = "garbage")) }
        invalid { validatePlannedIncome(record(frequency = "ONCE", yearMonth = "2026-05", monthOfYear = 5)) }
    }

    @Test
    fun unknownBasisOrFrequency_rejected() {
        validatePlannedIncome(record(basis = "PER_WORKING_DAY"))
        invalid { validatePlannedIncome(record(basis = "HOURLY")) }
        invalid { validatePlannedIncome(record(basis = "fixed")) }
        invalid { validatePlannedIncome(record(frequency = "WEEKLY")) }
    }

    @Test
    fun skipMonths_atMostTwelve_eachOneToTwelve() {
        validatePlannedIncome(record(skipMonths = (1..12).toList()))
        invalid { validatePlannedIncome(record(skipMonths = listOf(0))) }
        invalid { validatePlannedIncome(record(skipMonths = listOf(13))) }
        invalid { validatePlannedIncome(record(skipMonths = List(13) { 1 })) }
    }

    @Test
    fun amountAndLabel_stillValidatedForAllShapes() {
        validatePlannedIncome(record(amount = MAX_AMOUNT_CENTS, basis = "PER_WORKING_DAY"))
        invalid { validatePlannedIncome(record(amount = 0, basis = "PER_WORKING_DAY")) }
        invalid { validatePlannedIncome(record(amount = MAX_AMOUNT_CENTS + 1)) }
        invalid { validatePlannedIncome(record(label = " ")) }
    }

    @Test
    fun toDomain_mapsAllFields() {
        val domain = record("PER_WORKING_DAY", "ONCE", yearMonth = "2026-12", skipMonths = listOf(3)).toDomain()

        assertEquals(IncomeBasis.PER_WORKING_DAY, domain.basis)
        assertEquals(IncomeFrequency.ONCE, domain.frequency)
        assertEquals(YearMonth.of(2026, 12), domain.yearMonth)
        assertEquals(setOf(3), domain.skipMonths)
    }

    @Test
    fun toDomain_unknownValues_fallBackToFixedMonthly() {
        val domain = record(basis = "???", frequency = "???", yearMonth = "nope").toDomain()

        assertEquals(IncomeBasis.FIXED, domain.basis)
        assertEquals(IncomeFrequency.MONTHLY, domain.frequency)
        assertNull(domain.yearMonth)
    }

    @Test
    fun toLocal_roundTrips_andSortsSkipMonths() {
        val line =
            PlannedIncome("p", "Pay", 5, 1, IncomeBasis.PER_WORKING_DAY, skipMonths = setOf(8, 2))

        assertEquals(listOf(2, 8), line.toLocal().skipMonths)
        assertEquals(line, line.toLocal().toDomain())
    }

    @Test
    fun oldShapeJson_decodesWithDefaults() {
        val json = Json { ignoreUnknownKeys = true }
        val oldShape = """{"id":"p","label":"Pay","amountCents":5,"order":0}"""
        val decoded =
            json.decodeFromString(LocalPlannedIncome.serializer(), oldShape)

        assertEquals("FIXED", decoded.basis)
        assertEquals("MONTHLY", decoded.frequency)
        assertNull(decoded.monthOfYear)
        assertNull(decoded.yearMonth)
        assertEquals(emptyList<Int>(), decoded.skipMonths)
        validatePlannedIncome(decoded)
    }
}
