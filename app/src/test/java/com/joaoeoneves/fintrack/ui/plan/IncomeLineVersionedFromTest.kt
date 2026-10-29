package com.joaoeoneves.fintrack.ui.plan

import com.joaoeoneves.fintrack.domain.model.AmountVersion
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.YearMonth

class IncomeLineVersionedFromTest {
    private val jan = YearMonth.of(2026, 1)
    private val mar = YearMonth.of(2026, 3)
    private val oct = YearMonth.of(2026, 10)

    private fun line(
        cents: Long,
        start: YearMonth? = null,
        managedKey: String? = null,
    ) = PlannedIncome("l", "Rent", cents, 0, managedKey = managedKey, startMonth = start)

    @Test
    fun newLine_startsAtTheChosenMonth() {
        val result = line(1_000).versionedFrom(null, oct)
        assertEquals(oct, result.startMonth)
        assertEquals(emptyList<AmountVersion>(), result.versions)
    }

    @Test
    fun unchangedAmount_addsNoVersion() {
        val result = line(1_000).versionedFrom(line(1_000, start = jan), oct)
        assertEquals(jan, result.startMonth)
        assertEquals(emptyList<AmountVersion>(), result.versions)
        assertEquals(1_000L, result.amountCents)
    }

    @Test
    fun changedAmount_versionsFromTheChosenMonth_keepingTheOldOne() {
        val result = line(1_500).versionedFrom(line(1_000, start = jan), oct)
        assertEquals(jan, result.startMonth)
        assertEquals(1_500L, result.amountCents)
        assertEquals(listOf(AmountVersion(jan, 1_000L), AmountVersion(oct, 1_500L)), result.versions)
    }

    @Test
    fun earlierMonth_movesTheStartBack() {
        val result = line(1_500).versionedFrom(line(1_000, start = mar), jan)
        assertEquals(jan, result.startMonth)
        assertEquals(AmountVersion(jan, 1_500L), result.versions.last())
    }

    @Test
    fun managedLine_isUntouched() {
        val built = line(9_999, managedKey = "salary")
        assertSame(built, built.versionedFrom(line(1_000, start = jan, managedKey = "salary"), oct))
    }
}
