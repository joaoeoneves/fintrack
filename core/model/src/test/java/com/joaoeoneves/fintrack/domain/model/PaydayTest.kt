package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class PaydayTest {
    private val oct = YearMonth.of(2026, 10)
    private val octPayday = LocalDate.of(2026, 10, 30)
    private val utc = ZoneOffset.UTC

    private fun line(
        id: String = "p1",
        cents: Long = 100_000L,
        managedKey: String? = null,
        basis: IncomeBasis = IncomeBasis.FIXED,
    ) = PlannedIncome(id, "Line $id", cents, 0, basis = basis, managedKey = managedKey)

    @Test
    fun id_isDeterministic_andRecognisable() {
        assertEquals("pay_p1_202610", paydayIncomeId("p1", oct))
        assertEquals("pay_p1_202601", paydayIncomeId("p1", YearMonth.of(2026, 1)))
        assertTrue(isPaydayIncomeId("pay_p1_202610"))
        assertFalse(isPaydayIncomeId("rec_r1_20260315"))
        assertFalse(isPaydayIncomeId("other"))
    }

    @Test
    fun paydayIncome_zeroOrNegative_isNull() {
        assertNull(line(cents = 0).paydayIncome(oct, 21, octPayday, utc))
        assertNull(line(cents = -5).paydayIncome(oct, 21, octPayday, utc))
        assertNull(line(cents = 100, basis = IncomeBasis.PER_WORKING_DAY).paydayIncome(oct, 0, octPayday, utc))
    }

    @Test
    fun paydayIncome_inactiveMonth_isNull() {
        val skipping = line().copy(skipMonths = setOf(10))

        assertNull(skipping.paydayIncome(oct, 21, octPayday, utc))
    }

    @Test
    fun paydayIncome_building_usesLabelAmountAndNoon() {
        val income = line(cents = 1_000, basis = IncomeBasis.PER_WORKING_DAY).paydayIncome(oct, 20, octPayday, utc)!!

        assertEquals("pay_p1_202610", income.id)
        assertEquals("Line p1", income.source)
        assertEquals(20_000L, income.amountCents)
        assertEquals(Instant.parse("2026-10-30T12:00:00Z"), income.date)
        assertNull(income.note)
    }

    @Test
    fun paydayIncome_dateStaysOnTheLocalDay_inAnyZone() {
        val zone = ZoneId.of("Pacific/Auckland")
        val income = line().paydayIncome(oct, 21, octPayday, zone)!!

        assertEquals(octPayday, income.date.atZone(zone).toLocalDate())
    }

    @Test
    fun paydayIncome_amountOverride_replacesTheAmount() {
        assertEquals(42L, line().paydayIncome(oct, 21, octPayday, utc, amountCents = 42L)!!.amountCents)
        assertNull(line().paydayIncome(oct, 21, octPayday, utc, amountCents = 0L))
    }

    // --- receivedIncomes / receivedOn ---

    private fun row(vararg lines: PlannedIncome) = incomeRowsIn(lines.toList(), oct, 21).first()

    private fun received(
        l: PlannedIncome,
        date: LocalDate = octPayday,
    ) = l.paydayIncome(oct, 21, date, utc)!!

    @Test
    fun received_singleLine_found() {
        val l = line()
        val r = row(l)

        assertEquals(listOf(received(l)), r.receivedIncomes(oct, 21, listOf(received(l))))
        assertEquals(octPayday, r.receivedOn(oct, 21, listOf(received(l)), utc))
    }

    @Test
    fun received_none_isNull() {
        val r = row(line())

        assertNull(r.receivedIncomes(oct, 21, emptyList()))
        assertNull(r.receivedOn(oct, 21, emptyList(), utc))
    }

    @Test
    fun received_otherMonthsId_doesNotCount() {
        val l = line()
        val september = l.paydayIncome(YearMonth.of(2026, 9), 21, LocalDate.of(2026, 9, 30), utc)!!

        assertNull(row(l).receivedIncomes(oct, 21, listOf(september)))
    }

    @Test
    fun received_multiLine_partial_isNotReceived() {
        val a = line("a", managedKey = MANAGED_NET_SALARY)
        val b = line("b", managedKey = MANAGED_NET_SALARY)
        val r = row(a, b)
        assertEquals(2, r.lines.size)

        assertNull(r.receivedIncomes(oct, 21, listOf(received(a))))
        assertEquals(2, r.receivedIncomes(oct, 21, listOf(received(a), received(b)))!!.size)
    }

    @Test
    fun received_multiLine_ignoresNonPayingLines_andUsesLatestDay() {
        val a = line("a", managedKey = MANAGED_NET_SALARY)
        val b = line("b", managedKey = MANAGED_NET_SALARY).copy(skipMonths = setOf(10))
        val c = line("c", managedKey = MANAGED_NET_SALARY)
        val r = row(a, b, c)
        val incomes = listOf(received(a, LocalDate.of(2026, 10, 20)), received(c, LocalDate.of(2026, 10, 25)))

        assertEquals(2, r.receivedIncomes(oct, 21, incomes)!!.size)
        assertEquals(LocalDate.of(2026, 10, 25), r.receivedOn(oct, 21, incomes, utc))
    }

    @Test
    fun received_rowPayingNothing_isNull() {
        val l = line().copy(skipMonths = setOf(10))

        assertNull(row(l).receivedIncomes(oct, 21, listOf(received(line()))))
    }
}
