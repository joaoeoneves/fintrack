package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class SalaryProfileLinesSyncTest {
    private val profile =
        SalaryProfile(
            baseGrossCents = 100_000,
            irsBasePpm = 100_000,
            irsSubsidiesPpm = 50_000,
            mealRatePerDayCents = 900,
            extras = listOf(testExtra(), fixedExtra()),
        )
    private val workingDays = 21

    private fun lines(
        p: SalaryProfile = profile,
        existing: List<PlannedIncome> = emptyList(),
    ) = salaryProfileLines(p, existing)

    private fun List<PlannedIncome>.byKey(key: String) = single { it.managedKey == key }

    private fun List<PlannedIncome>.yearTotal(year: Int = 2026) =
        (1..12).sumOf { m -> sumOf { it.amountIn(YearMonth.of(year, m), workingDays) } }

    @Test
    fun netSalaryIsTheBase_andDuodecimosIsTheRest() {
        val result = lines()

        assertEquals(79_000, result.byKey(MANAGED_NET_SALARY).amountCents)
        val duo = result.byKey(MANAGED_DUODECIMOS)
        assertEquals(7_000, duo.amountCents)
        assertEquals(IncomeBasis.FIXED, duo.basis)
        assertEquals(IncomeFrequency.MONTHLY, duo.frequency)
        assertEquals("Subsidies (twelfths)", duo.label)
    }

    @Test
    fun duoLine_usesTheLocalizedLabel() {
        val result = salaryProfileLines(profile, emptyList(), SalaryLineLabels(duodecimos = "Duodecimos"))

        assertEquals("Duodecimos", result.byKey(MANAGED_DUODECIMOS).label)
    }

    @Test
    fun noDuoLine_whenThereIsNoSpread() {
        val result = lines(profile.copy(subsidySpreadPpm = SPREAD_NONE_PPM))

        assertTrue(result.none { it.managedKey == MANAGED_DUODECIMOS })
    }

    @Test
    fun noDuoLine_inNetMode() {
        val result = lines(SalaryProfile(inputMode = SalaryInputMode.NET, netMonthlyCents = 90_000))

        assertEquals(listOf(MANAGED_NET_SALARY), result.map { it.managedKey })
        assertEquals(90_000, result.single().amountCents)
    }

    @Test
    fun amountsOverAllManagedLines_matchBaseAndDuoInOneLine() {
        for (spread in SUBSIDY_SPREADS_PPM) {
            val p =
                profile.copy(subsidySpreadPpm = spread, mealRatePerDayCents = null, extras = emptyList())
            val b = requireNotNull(salaryBreakdownOf(p))
            val combined =
                PlannedIncome("x", "x", b.netMonthlyCents, 0, managedKey = MANAGED_NET_SALARY) // one line holding both
            val subsidies = lines(p).filter { it.managedKey == MANAGED_HOLIDAY || it.managedKey == MANAGED_CHRISTMAS }
            val split = lines(p)

            for (m in 1..12) {
                val month = YearMonth.of(2026, m)
                val expected =
                    combined.amountIn(month, workingDays) + subsidies.sumOf { it.amountIn(month, workingDays) }
                assertEquals("spread $spread month $m", expected, split.sumOf { it.amountIn(month, workingDays) })
            }
            assertEquals(
                12 * b.netMonthlyCents + 2 * b.holidayLumpNetCents,
                split.yearTotal(),
            )
        }
    }

    @Test
    fun junAndDec_carryTheLumpsOnTopOfTheMonthlyLines() {
        val split = lines()

        val june = split.sumOf { it.amountIn(YearMonth.of(2026, 6), workingDays) }
        val august = split.sumOf { it.amountIn(YearMonth.of(2026, 8), workingDays) }
        val december = split.sumOf { it.amountIn(YearMonth.of(2026, 12), workingDays) }

        val lump = requireNotNull(salaryBreakdownOf(profile)).holidayLumpNetCents
        assertEquals(august + lump, june)
        assertEquals(august + lump, december)
    }

    @Test
    fun sync_inSync_isEmpty() {
        val existing = lines()

        val sync = managedLinesSync(profile, existing)

        assertTrue(sync.isEmpty)
        assertTrue(sync.upserts.isEmpty())
        assertTrue(sync.deletes.isEmpty())
    }

    @Test
    fun sync_newLinesComeBeforeModifiedOnes() {
        val current = lines().filterNot { it.managedKey == MANAGED_DUODECIMOS }
        val staleNet = current.byKey(MANAGED_NET_SALARY).copy(amountCents = 86_000)
        val existing = current.map { if (it.id == MANAGED_NET_SALARY) staleNet else it }

        val sync = managedLinesSync(profile, existing)

        assertEquals(listOf(MANAGED_DUODECIMOS, MANAGED_NET_SALARY), sync.upserts.map { it.id })
        assertEquals(79_000, sync.upserts.last().amountCents)
        assertTrue(sync.deletes.isEmpty())
        assertFalse(sync.isEmpty)
    }

    @Test
    fun sync_onlyChangedLinesAreWritten() {
        val existing = lines()
        val changed = profile.copy(extras = listOf(testExtra(), fixedExtra(cents = 20_000)))

        val sync = managedLinesSync(changed, existing)

        assertEquals(listOf(managedExtraKey("bonus", 5)), sync.upserts.map { it.id })
        assertEquals(20_000, sync.upserts.single().amountCents)
    }

    @Test
    fun sync_renamingAnExtra_relabelsItsLines_keepingOrder() {
        val existing =
            lines().map {
                if (it.id == managedExtraKey("bonus", 5)) it.copy(order = 42) else it
            }
        val renamed = profile.copy(extras = listOf(testExtra(), fixedExtra(name = "Prize")))

        val sync = managedLinesSync(renamed, existing)

        val line = sync.upserts.single()
        assertEquals("Prize", line.label)
        assertEquals(42, line.order)
        assertTrue(sync.deletes.isEmpty())
    }

    @Test
    fun sync_isIdempotent() {
        assertTrue(managedLinesSync(profile, lines()).isEmpty)
    }

    @Test
    fun sync_deletingAnExtraOrAMonth_deletesItsLines() {
        val existing = lines()

        val noBonus = managedLinesSync(profile.copy(extras = listOf(testExtra())), existing)
        val fewer = profile.copy(extras = listOf(testExtra(months = setOf(1, 4)), fixedExtra()))
        val fewerMonths = managedLinesSync(fewer, existing)

        assertEquals(listOf(managedExtraKey("bonus", 5)), noBonus.deletes)
        assertEquals(setOf(managedExtraKey("flex", 7), managedExtraKey("flex", 10)), fewerMonths.deletes.toSet())
    }

    @Test
    fun sync_legacyFlexProfitAndBonusLines_areDeleted() {
        val legacyKeys = listOf("profile_flex_q1", "profile_profit_share_4", "profile_bonus", "profile_profit_share")
        val legacy = legacyKeys.mapIndexed { i, k -> PlannedIncome(k, "old", 5, 80 + i, managedKey = k) }

        val sync = managedLinesSync(profile, lines() + legacy)

        assertEquals(legacyKeys.toSet(), sync.deletes.toSet())
    }

    @Test
    fun sync_deletesLegacyProfitShareAndMismatchedIds() {
        val legacy = PlannedIncome(LEGACY_PROFIT_SHARE_KEY, "Profit", 5, 90, managedKey = LEGACY_PROFIT_SHARE_KEY)
        val oddId = PlannedIncome("random-id", "Odd", 5, 91, managedKey = MANAGED_MEAL)
        val own = PlannedIncome("own", "Own", 5, 92)

        val sync = managedLinesSync(profile, lines() + legacy + oddId + own)

        assertTrue(LEGACY_PROFIT_SHARE_KEY in sync.deletes)
        assertTrue("random-id" in sync.deletes)
        assertFalse("own" in sync.deletes)
    }

    @Test
    fun sync_profileWithoutRequiredAmount_deletesEveryManagedLine() {
        val sync = managedLinesSync(SalaryProfile(), lines())

        assertTrue(sync.upserts.isEmpty())
        assertEquals(lines().map { it.id }.toSet(), sync.deletes.toSet())
    }

    private companion object {
        const val LEGACY_PROFIT_SHARE_KEY = "profile_profit_share"
    }
}
