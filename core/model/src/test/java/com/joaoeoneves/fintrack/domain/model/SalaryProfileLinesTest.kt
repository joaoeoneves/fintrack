package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalaryProfileLinesTest {
    private val full =
        SalaryProfile(
            baseGrossCents = 100_000,
            irsBasePpm = 100_000,
            irsSubsidiesPpm = 50_000,
            mealRatePerDayCents = 900,
            flexPpm = 70_000,
            profitShareMultiplierPpm = 500_000,
            profitShareMonths = setOf(4),
            bonusCents = 10_000,
            bonusMonth = 5,
        )

    private fun lines(
        profile: SalaryProfile = full,
        existing: List<PlannedIncome> = emptyList(),
    ) = salaryProfileLines(profile, existing)

    private fun List<PlannedIncome>.byKey(key: String) = single { it.managedKey == key }

    @Test
    fun managedKeys_listsEveryKey() {
        assertEquals(23, MANAGED_KEYS.size)
        assertEquals(MANAGED_KEYS.size, MANAGED_KEYS.toSet().size)
        assertEquals("profile_flex_q1", managedFlexKey(0))
        assertEquals("profile_flex_q4", managedFlexKey(3))
        assertEquals("profile_profit_share_4", managedProfitShareKey(4))
        assertTrue(MANAGED_PROFIT_SHARE in MANAGED_KEYS)
    }

    @Test
    fun fullProfile_producesEveryLine_withIdEqualToKey() {
        val result = lines()

        val expected =
            MANAGED_KEYS.toSet() - MANAGED_PROFIT_SHARE - (1..12).map(::managedProfitShareKey).toSet() +
                managedProfitShareKey(4)
        assertEquals(expected, result.map { it.managedKey }.toSet())
        assertTrue(result.all { it.id == it.managedKey })
    }

    @Test
    fun netSalaryLine_isMonthlyAndFixed() {
        val net = lines().byKey(MANAGED_NET_SALARY)

        assertEquals(79_000, net.amountCents)
        assertEquals(IncomeFrequency.MONTHLY, net.frequency)
        assertEquals(IncomeBasis.FIXED, net.basis)
        assertEquals("Net salary", net.label)
    }

    @Test
    fun mealLine_isPerWorkingDay_withNoSkipMonths() {
        val meal = lines().byKey(MANAGED_MEAL)

        assertEquals(900, meal.amountCents)
        assertEquals(IncomeBasis.PER_WORKING_DAY, meal.basis)
        assertEquals(IncomeFrequency.MONTHLY, meal.frequency)
        assertTrue(meal.skipMonths.isEmpty())
    }

    @Test
    fun mealLine_ignoresStoredSkipMonths() {
        val meal = lines(full.copy(mealSkipMonths = setOf(1, 8))).byKey(MANAGED_MEAL)

        assertTrue(meal.skipMonths.isEmpty())
    }

    @Test
    fun subsidyLines_areYearlyInTheirMonths() {
        val result = lines(full.copy(holidaySubsidyMonth = 7, christmasSubsidyMonth = 11))

        assertEquals(IncomeFrequency.YEARLY, result.byKey(MANAGED_HOLIDAY).frequency)
        assertEquals(7, result.byKey(MANAGED_HOLIDAY).monthOfYear)
        assertEquals(11, result.byKey(MANAGED_CHRISTMAS).monthOfYear)
        assertEquals(42_000, result.byKey(MANAGED_HOLIDAY).amountCents)
    }

    @Test
    fun profitShareAndBonus_useTheirMonths_orTheDefault() {
        val result = lines()
        val defaulted = lines(full.copy(profitShareMonths = emptySet(), bonusMonth = null))

        assertEquals(50_000, result.byKey(managedProfitShareKey(4)).amountCents)
        assertEquals(4, result.byKey(managedProfitShareKey(4)).monthOfYear)
        assertEquals(10_000, result.byKey(MANAGED_BONUS).amountCents)
        assertEquals(5, result.byKey(MANAGED_BONUS).monthOfYear)
        assertEquals(
            DEFAULT_EXTRA_PAY_MONTH,
            defaulted.byKey(managedProfitShareKey(DEFAULT_EXTRA_PAY_MONTH)).monthOfYear,
        )
        assertEquals(DEFAULT_EXTRA_PAY_MONTH, defaulted.byKey(MANAGED_BONUS).monthOfYear)
    }

    @Test
    fun flexLines_splitTheAnnual() {
        val result = lines()
        val amounts = List(FLEX_PAYMENTS) { result.byKey(managedFlexKey(it)).amountCents }

        assertEquals(listOf(24_500L, 24_500L, 24_500L, 24_500L), amounts)
        assertEquals("Flexible benefits", result.byKey("profile_flex_q1").label)
    }

    @Test
    fun zeroInputs_omitTheirLines() {
        val minimal = lines(SalaryProfile(baseGrossCents = 100_000, subsidySpreadPpm = SPREAD_FULL_PPM))

        assertEquals(listOf(MANAGED_NET_SALARY, MANAGED_DUODECIMOS), minimal.map { it.managedKey })
    }

    @Test
    fun fullSpread_omitsSubsidyLines() {
        val result = lines(full.copy(subsidySpreadPpm = SPREAD_FULL_PPM))

        assertTrue(result.none { it.managedKey == MANAGED_HOLIDAY || it.managedKey == MANAGED_CHRISTMAS })
    }

    @Test
    fun missingRequiredAmount_producesNoLines() {
        assertTrue(lines(SalaryProfile()).isEmpty())
        assertTrue(lines(SalaryProfile(inputMode = SalaryInputMode.NET)).isEmpty())
    }

    @Test
    fun idempotent_applyingTheResultAgainChangesNothing() {
        val first = lines()
        val second = lines(existing = first)

        assertEquals(first, second)
    }

    @Test
    fun existingManagedLine_keepsLabelEarmarkAndOrder() {
        val edited =
            PlannedIncome(
                id = MANAGED_NET_SALARY,
                label = "My pay",
                amountCents = 1,
                order = 7,
                earmark = Earmark.Group("g1"),
                managedKey = MANAGED_NET_SALARY,
            )

        val net = lines(existing = listOf(edited)).byKey(MANAGED_NET_SALARY)

        assertEquals("My pay", net.label)
        assertEquals(Earmark.Group("g1"), net.earmark)
        assertEquals(7, net.order)
        assertEquals(79_000, net.amountCents)
    }

    @Test
    fun newLines_getOrdersAfterTheExistingOnes() {
        val own = PlannedIncome("own", "Own", 1, order = 4)

        val result = lines(existing = listOf(own))

        assertEquals(5, result.first().order)
        assertEquals(result.indices.map { it + 5 }, result.map { it.order })
    }

    @Test
    fun unmanagedLine_withAManagedId_isNotTreatedAsManaged() {
        val own = PlannedIncome(MANAGED_NET_SALARY, "Mine", 1, order = 0)

        val net = lines(existing = listOf(own)).byKey(MANAGED_NET_SALARY)

        assertEquals("Net salary", net.label)
        assertNull(net.earmark)
    }

    @Test
    fun labels_areUsedForNewLines() {
        val labels = SalaryLineLabels(netSalary = "Liquido", flex = "Flex")

        val result = salaryProfileLines(full, emptyList(), labels)

        assertEquals("Liquido", result.byKey(MANAGED_NET_SALARY).label)
        assertEquals("Flex", result.byKey("profile_flex_q2").label)
    }

    @Test
    fun stale_nothingWhenStillProduced() {
        assertTrue(staleManagedIds(full, lines()).isEmpty())
    }

    @Test
    fun stale_listsManagedLinesTheProfileNoLongerProduces() {
        val existing = lines()

        val stale = staleManagedIds(full.copy(bonusCents = null, flexPpm = null), existing)

        assertEquals(
            (listOf(MANAGED_BONUS) + List(FLEX_PAYMENTS, ::managedFlexKey)).toSet(),
            stale.toSet(),
        )
    }

    @Test
    fun stale_neverListsUnmanagedLines() {
        val own = PlannedIncome("own", "Own", 1, 0)

        assertTrue(staleManagedIds(null, listOf(own)).isEmpty())
    }

    @Test
    fun stale_noProfile_listsAllManagedLines() {
        val existing = lines() + PlannedIncome("own", "Own", 1, 99)

        val managedIds = existing.filter { it.isManaged }.map { it.id }.toSet()

        assertEquals(managedIds, staleManagedIds(null, existing).toSet())
    }

    @Test
    fun subsidyLines_orderedEarlierFirst_sharedLabel() {
        val result = lines(full.copy(holidaySubsidyMonth = 11, christmasSubsidyMonth = 2))

        assertEquals(2, result.byKey(MANAGED_HOLIDAY).monthOfYear)
        assertEquals(11, result.byKey(MANAGED_CHRISTMAS).monthOfYear)
        assertEquals("Subsidy", result.byKey(MANAGED_HOLIDAY).label)
        assertEquals("Subsidy", result.byKey(MANAGED_CHRISTMAS).label)
    }

    @Test
    fun subsidyLines_singleMonth_bothInTheSameMonth() {
        val result = lines(full.copy(holidaySubsidyMonth = 7, christmasSubsidyMonth = 7))

        assertEquals(7, result.byKey(MANAGED_HOLIDAY).monthOfYear)
        assertEquals(7, result.byKey(MANAGED_CHRISTMAS).monthOfYear)
    }

    private fun profitLines(profile: SalaryProfile) = lines(profile).filter { it.id.startsWith(MANAGED_PROFIT_SHARE) }

    @Test
    fun profitShare_oneMonth_getsTheWholeAmount() {
        val result = profitLines(full.copy(profitShareMonths = setOf(6)))

        assertEquals(listOf("profile_profit_share_6"), result.map { it.managedKey })
        assertEquals(listOf(50_000L), result.map { it.amountCents })
    }

    @Test
    fun profitShare_twoMonths_splitEvenly() {
        val result = profitLines(full.copy(profitShareMonths = setOf(9, 3)))

        assertEquals(listOf(3, 9), result.map { it.monthOfYear })
        assertEquals(listOf(25_000L, 25_000L), result.map { it.amountCents })
    }

    @Test
    fun profitShare_threeMonths_remainderOnTheLast() {
        val total = lines(full).byKey(managedProfitShareKey(4)).amountCents
        val result = profitLines(full.copy(profitShareMonths = setOf(3, 6, 9)))

        assertEquals(listOf(3, 6, 9), result.map { it.monthOfYear })
        assertEquals(listOf(16_666L, 16_666L, 16_668L), result.map { it.amountCents })
        assertEquals(total, result.sumOf { it.amountCents })
    }

    @Test
    fun profitShare_legacySingleMonth_isUsedWhenNoMonthsAreSet() {
        val legacy = full.copy(profitShareMonths = emptySet(), profitShareMonth = 8)

        assertEquals(listOf(8), legacy.effectiveProfitShareMonths)
        assertEquals(listOf("profile_profit_share_8"), profitLines(legacy).map { it.managedKey })
    }

    @Test
    fun profitShare_monthsWinOverLegacy_andDefaultIsMarch() {
        val both = SalaryProfile(profitShareMonths = setOf(5, 2), profitShareMonth = 8)

        assertEquals(listOf(2, 5), both.effectiveProfitShareMonths)
        assertEquals(listOf(DEFAULT_EXTRA_PAY_MONTH), SalaryProfile().effectiveProfitShareMonths)
        assertEquals(3, DEFAULT_EXTRA_PAY_MONTH)
    }

    @Test
    fun stale_legacyProfitShareKey_isRemoved() {
        val legacy =
            PlannedIncome(MANAGED_PROFIT_SHARE, "Profit share", 1, 0, managedKey = MANAGED_PROFIT_SHARE)

        assertEquals(listOf(MANAGED_PROFIT_SHARE), staleManagedIds(full, listOf(legacy) + lines()))
    }

    @Test
    fun stale_profitShareMonthsDropped_areRemoved() {
        val existing = lines(full.copy(profitShareMonths = setOf(3, 6)))

        val stale = staleManagedIds(full.copy(profitShareMonths = setOf(3)), existing)

        assertEquals(listOf(managedProfitShareKey(6)), stale)
    }
}
