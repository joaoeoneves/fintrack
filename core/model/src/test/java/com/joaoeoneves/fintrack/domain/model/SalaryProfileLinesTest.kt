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
            extras = listOf(testExtra(), multipleExtra(), fixedExtra()),
        )

    private fun lines(
        profile: SalaryProfile = full,
        existing: List<PlannedIncome> = emptyList(),
    ) = salaryProfileLines(profile, existing)

    private fun List<PlannedIncome>.byKey(key: String) = single { it.managedKey == key }

    @Test
    fun managedKeys_helpers() {
        assertEquals("profile_extra_abc_7", managedExtraKey("abc", 7))
    }

    @Test
    fun fullProfile_producesEveryLine_withIdEqualToKey() {
        val result = lines()

        val expected =
            setOf(MANAGED_NET_SALARY, MANAGED_DUODECIMOS, MANAGED_MEAL, MANAGED_HOLIDAY, MANAGED_CHRISTMAS) +
                listOf(1, 4, 7, 10).map { managedExtraKey("flex", it) } +
                managedExtraKey("profit", 4) + managedExtraKey("bonus", 5)
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
        assertEquals(39_500, result.byKey(MANAGED_HOLIDAY).amountCents)
    }

    @Test
    fun extraLines_areYearlyInTheirMonths_namedAfterTheExtra() {
        val result = lines()

        val profit = result.byKey(managedExtraKey("profit", 4))
        assertEquals(50_000, profit.amountCents)
        assertEquals(4, profit.monthOfYear)
        assertEquals(IncomeFrequency.YEARLY, profit.frequency)
        assertEquals("Profit share", profit.label)
        assertEquals(10_000, result.byKey(managedExtraKey("bonus", 5)).amountCents)
    }

    @Test
    fun flexLines_splitTheAnnual() {
        val result = lines()
        val amounts = listOf(1, 4, 7, 10).map { result.byKey(managedExtraKey("flex", it)).amountCents }

        assertEquals(listOf(24_500L, 24_500L, 24_500L, 24_500L), amounts)
        assertEquals("Flexible benefits", result.byKey("profile_extra_flex_1").label)
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
    fun existingManagedLine_keepsLabelAndOrder() {
        val edited =
            PlannedIncome(
                id = MANAGED_NET_SALARY,
                label = "My pay",
                amountCents = 1,
                order = 7,
                managedKey = MANAGED_NET_SALARY,
            )

        val net = lines(existing = listOf(edited)).byKey(MANAGED_NET_SALARY)

        assertEquals("My pay", net.label)
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
    }

    @Test
    fun labels_areUsedForNewLines() {
        val labels = SalaryLineLabels(netSalary = "Liquido")

        val result = salaryProfileLines(full, emptyList(), labels)

        assertEquals("Liquido", result.byKey(MANAGED_NET_SALARY).label)
        assertEquals("Flexible benefits", result.byKey("profile_extra_flex_4").label)
    }

    @Test
    fun stale_nothingWhenStillProduced() {
        assertTrue(staleManagedIds(full, lines()).isEmpty())
    }

    @Test
    fun stale_listsManagedLinesTheProfileNoLongerProduces() {
        val existing = lines()

        val stale = staleManagedIds(full.copy(extras = listOf(multipleExtra())), existing)

        assertEquals(
            (listOf(1, 4, 7, 10).map { managedExtraKey("flex", it) } + managedExtraKey("bonus", 5)).toSet(),
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
    fun subsidyLines_useProfileMonths_distinctLabels() {
        val result = lines(full.copy(holidaySubsidyMonth = 11, christmasSubsidyMonth = 2))

        // The lines use the profile's months directly, no reordering.
        assertEquals(11, result.byKey(MANAGED_HOLIDAY).monthOfYear)
        assertEquals(2, result.byKey(MANAGED_CHRISTMAS).monthOfYear)
        assertEquals("Holiday subsidy", result.byKey(MANAGED_HOLIDAY).label)
        assertEquals("Christmas subsidy", result.byKey(MANAGED_CHRISTMAS).label)
    }

    @Test
    fun subsidyLines_singleMonth_bothInTheSameMonth() {
        val result = lines(full.copy(holidaySubsidyMonth = 7, christmasSubsidyMonth = 7))

        assertEquals(7, result.byKey(MANAGED_HOLIDAY).monthOfYear)
        assertEquals(7, result.byKey(MANAGED_CHRISTMAS).monthOfYear)
    }

    private fun profitLines(months: Set<Int>) =
        lines(full.copy(extras = listOf(multipleExtra(months = months))))
            .filter { it.id.startsWith("profile_extra_profit_") }

    @Test
    fun extra_oneMonth_getsTheWholeAmount() {
        val result = profitLines(setOf(6))

        assertEquals(listOf("profile_extra_profit_6"), result.map { it.managedKey })
        assertEquals(listOf(50_000L), result.map { it.amountCents })
    }

    @Test
    fun extra_twoMonths_splitEvenly() {
        val result = profitLines(setOf(9, 3))

        assertEquals(listOf(3, 9), result.map { it.monthOfYear })
        assertEquals(listOf(25_000L, 25_000L), result.map { it.amountCents })
    }

    @Test
    fun extra_threeMonths_remainderOnTheLast() {
        val result = profitLines(setOf(3, 6, 9))

        assertEquals(listOf(3, 6, 9), result.map { it.monthOfYear })
        assertEquals(listOf(16_666L, 16_666L, 16_668L), result.map { it.amountCents })
        assertEquals(50_000L, result.sumOf { it.amountCents })
    }

    @Test
    fun stale_legacyKeys_areRemoved() {
        val legacyKeys = listOf("profile_profit_share", "profile_bonus", "profile_flex_q2")
        val legacy =
            legacyKeys.map {
                PlannedIncome(it, "old", 1, 0, managedKey = it)
            }

        assertEquals(
            legacyKeys,
            staleManagedIds(full, legacy + lines()),
        )
    }

    @Test
    fun stale_extraMonthsDropped_areRemoved() {
        val existing = lines(full.copy(extras = listOf(multipleExtra(months = setOf(3, 6)))))

        val stale = staleManagedIds(full.copy(extras = listOf(multipleExtra(months = setOf(3)))), existing)

        assertEquals(listOf(managedExtraKey("profit", 6)), stale)
    }

    @Test
    fun extraLines_relabelOnRename_keepingOrder() {
        val first =
            lines().map {
                if (it.id == "profile_extra_profit_4") it.copy(order = 55) else it
            }
        val renamed = full.copy(extras = listOf(testExtra(), multipleExtra(name = "Dividends"), fixedExtra()))

        val line = lines(renamed, first).byKey("profile_extra_profit_4")

        assertEquals("Dividends", line.label)
        assertEquals(55, line.order)
    }

    @Test
    fun isValidManagedKey_acceptsFixedAndExtraKeys() {
        val ok =
            listOf(MANAGED_NET_SALARY, MANAGED_DUODECIMOS, MANAGED_MEAL, MANAGED_HOLIDAY, MANAGED_CHRISTMAS) +
                listOf("profile_extra_abc123_1", "profile_extra_a_12")
        assertTrue(ok.all(::isValidManagedKey))
    }

    @Test
    fun isValidManagedKey_rejectsBadKeys() {
        val bad =
            listOf(
                "",
                "profile_unknown",
                "profile_extra_",
                "profile_extra__3",
                "profile_extra_abc_0",
                "profile_extra_abc_13",
                "profile_extra_abc",
                "profile_extra_a-b_3",
                "profile_extra_${"x".repeat(21)}_3",
                "profile_flex_q5",
                "profile_flex_q1",
                "profile_bonus",
                "profile_profit_share",
                "profile_profit_share_12",
                "profile_extra_abc_x",
            )
        assertTrue(bad.none(::isValidManagedKey))
    }

    @Test
    fun parseManagedExtraKey_roundTripsAndRejects() {
        assertEquals("abc" to 7, parseManagedExtraKey(managedExtraKey("abc", 7)))
        assertEquals("a1" to 12, parseManagedExtraKey("profile_extra_a1_12"))
        assertNull(parseManagedExtraKey("profile_extra_abc_0"))
        assertNull(parseManagedExtraKey("profile_extra_abc_13"))
        assertNull(parseManagedExtraKey("profile_extra_a_b_3"))
        assertNull(parseManagedExtraKey("profile_extra_3"))
        assertNull(parseManagedExtraKey("profile_bonus"))
        assertNull(parseManagedExtraKey("other_abc_3"))
    }
}

class SalaryProfileLinesAutoRecordTest {
    private val profile = SalaryProfile(baseGrossCents = 100_000, irsBasePpm = 100_000, mealRatePerDayCents = 900)

    private fun List<PlannedIncome>.byKey(key: String) = single { it.managedKey == key }

    @Test
    fun existingManagedLines_keepTheirAutoRecordAndPayDay() {
        val net =
            salaryProfileLines(profile, emptyList()).byKey(MANAGED_NET_SALARY).copy(autoRecord = true, payDay = 25)
        val meal = salaryProfileLines(profile, emptyList()).byKey(MANAGED_MEAL).copy(autoRecord = false, payDay = 3)

        val result = salaryProfileLines(profile, listOf(net, meal))

        assertTrue(result.byKey(MANAGED_NET_SALARY).autoRecord)
        assertEquals(25, result.byKey(MANAGED_NET_SALARY).payDay)
        assertEquals(false, result.byKey(MANAGED_MEAL).autoRecord)
        assertEquals(3, result.byKey(MANAGED_MEAL).payDay)
    }

    @Test
    fun newManagedLines_inheritFromTheNetSalaryLine() {
        val net =
            salaryProfileLines(profile, emptyList()).byKey(MANAGED_NET_SALARY).copy(autoRecord = true, payDay = 25)

        val result = salaryProfileLines(profile, listOf(net))

        assertTrue(result.byKey(MANAGED_MEAL).autoRecord)
        assertEquals(25, result.byKey(MANAGED_MEAL).payDay)
    }

    @Test
    fun withoutANetSalaryLine_newLinesDefaultOff() {
        val result = salaryProfileLines(profile, emptyList())

        assertTrue(result.none { it.autoRecord })
        assertTrue(result.all { it.payDay == null })
    }
}
