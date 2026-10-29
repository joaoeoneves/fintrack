package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.EmploymentSector
import com.joaoeoneves.fintrack.domain.model.MealPayment
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** The 5.2 fields in the guest file: round-trips, defaults for old files and unknown enums, validation. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MealCardRecordsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val valid = LocalSalaryProfile(baseGrossCents = 100_000)

    private fun invalid(profile: LocalSalaryProfile) {
        assertThrows(IllegalArgumentException::class.java) { validateSalaryProfile(profile) }
    }

    @Test
    fun profile_roundTrips() {
        val profile =
            SalaryProfile(
                baseGrossCents = 100_000,
                sector = EmploymentSector.PUBLIC,
                mealPayment = MealPayment.WITH_SALARY,
                otherDeductionsPpm = 35_000,
            )

        val local = profile.toLocal()

        assertEquals("PUBLIC", local.sector)
        assertEquals("WITH_SALARY", local.mealPayment)
        assertEquals(35_000, local.otherDeductionsPpm)
        assertEquals(profile, local.toDomain())
    }

    @Test
    fun profile_defaultsMapToTheDefaults() {
        val domain = LocalSalaryProfile().toDomain()

        assertNull(domain.sector)
        assertEquals(MealPayment.CARD, domain.mealPayment)
        assertEquals(0, domain.otherDeductionsPpm)
        assertNull(SalaryProfile().toLocal().sector)
    }

    @Test
    fun profile_unknownEnums_fallBackToTheDefaults() {
        val domain = LocalSalaryProfile(sector = "FREELANCE", mealPayment = "CASH").toDomain()

        assertNull(domain.sector)
        assertEquals(MealPayment.CARD, domain.mealPayment)
    }

    @Test
    fun profile_v19Json_decodesWithDefaults() {
        val decoded = json.decodeFromString<LocalSalaryProfile>("""{"inputMode":"GROSS","baseGrossCents":100000}""")

        assertNull(decoded.sector)
        assertEquals("CARD", decoded.mealPayment)
        assertEquals(0, decoded.otherDeductionsPpm)
    }

    @Test
    fun validate_sectorAndMealPayment() {
        validateSalaryProfile(valid.copy(sector = "PRIVATE"))
        validateSalaryProfile(valid.copy(sector = "PUBLIC", mealPayment = "WITH_SALARY"))
        invalid(valid.copy(sector = "FREELANCE"))
        invalid(valid.copy(sector = ""))
        invalid(valid.copy(mealPayment = "CASH"))
        invalid(valid.copy(mealPayment = "card"))
    }

    @Test
    fun validate_otherDeductions_rangeIsZeroToOneMillion() {
        validateSalaryProfile(valid.copy(otherDeductionsPpm = 0))
        validateSalaryProfile(valid.copy(otherDeductionsPpm = 1_000_000))
        invalid(valid.copy(otherDeductionsPpm = -1))
        invalid(valid.copy(otherDeductionsPpm = 1_000_001))
    }

    @Test
    fun line_flagRoundTripsAndV19JsonDefaultsToOff() {
        val line = LocalPlannedIncome("p", "Pay", 1_000, 0, payDay = 1, payDayEarlierIfClosed = true)

        assertTrue(line.toDomain().payDayEarlierIfClosed)
        assertTrue(line.toDomain().toLocal().payDayEarlierIfClosed)
        val old = json.decodeFromString<LocalPlannedIncome>("""{"id":"p","label":"Pay","amountCents":1000,"order":0}""")
        assertFalse(old.payDayEarlierIfClosed)
        assertFalse(old.toDomain().payDayEarlierIfClosed)
        validatePlannedIncome(line)
    }

    @Test
    fun expense_flagRoundTripsAndV19JsonDefaultsToBank() {
        val at = "2026-10-01T12:00:00Z"
        val card = LocalExpense("e", "Lunch", 500, "FOOD", at, paidWithMealCard = true)

        assertTrue(requireNotNull(card.toDomainOrNull()).paidWithMealCard)
        assertTrue(requireNotNull(card.toDomainOrNull()).toLocal().paidWithMealCard)
        val old =
            json.decodeFromString<LocalExpense>(
                """{"id":"e","name":"Lunch","amountCents":500,"categoryId":"FOOD","date":"$at"}""",
            )
        assertFalse(old.paidWithMealCard)
        assertFalse(requireNotNull(old.toDomainOrNull()).paidWithMealCard)
        assertEquals(Instant.parse(at), requireNotNull(card.toDomainOrNull()).date)
    }

    @Test
    fun category_flagRoundTripsAndV19JsonDefaultsToOff() {
        val custom = LocalCategory("c1", "Canteen", "home", "teal", "SPENDING", 1, mealCardByDefault = true)
        val builtIn = BuiltInCategories.FOOD.toLocal().copy(mealCardByDefault = true)

        assertTrue(requireNotNull(custom.toDomainOrNull()).mealCardByDefault)
        assertTrue(requireNotNull(builtIn.toDomainOrNull()).mealCardByDefault)
        assertTrue(requireNotNull(custom.toDomainOrNull()).toLocal().mealCardByDefault)
        val old =
            json.decodeFromString<LocalCategory>(
                """{"id":"c1","name":"Canteen","iconKey":"home","colorKey":"teal","kind":"SPENDING","order":1}""",
            )
        assertFalse(old.mealCardByDefault)
        assertFalse(requireNotNull(old.toDomainOrNull()).mealCardByDefault)
    }

    @Test
    fun v19Document_decodesWholeWithDefaults() {
        val text =
            """{"schemaVersion":19,"revision":3,
            "expenses":[{"id":"e","name":"Lunch","amountCents":500,"categoryId":"FOOD","date":"2026-10-01T12:00:00Z"}],
            "salaryProfile":{"inputMode":"GROSS","baseGrossCents":100000}}"""

        val doc = json.decodeFromString<LocalDataDocument>(text)

        assertEquals(19, doc.schemaVersion)
        assertFalse(doc.expenses.single().paidWithMealCard)
        assertEquals("CARD", doc.salaryProfile?.mealPayment)
    }
}
