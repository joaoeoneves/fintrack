package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

/** The "Valido desde" start month and amount versions as the plan calculations consume them. */
class EffectiveFromWiringTest : MonthlyPlanTestBase() {
    private fun ym(
        y: Int,
        m: Int,
    ) = YearMonth.of(y, m)

    private val sep = ym(2026, 9)
    private val oct = ym(2026, 10)
    private val on = IncomeFunding(true) { 20 }
    private val off = IncomeFunding(false) { 20 }

    private fun salary(
        startMonth: YearMonth? = null,
        versions: List<AmountVersion> = emptyList(),
        cents: Long = 100_000L,
    ) = PlannedIncome(
        "sal",
        "Salary",
        cents,
        0,
        managedKey = "salary",
        startMonth = startMonth,
        versions = versions,
    )

    @Test
    fun amountIn_shifted_lineStartingOctober_fundsOctoberFromSeptemberPayday() {
        val line = salary(startMonth = oct)
        assertEquals(100_000L, on.amountIn(line, oct))
        assertEquals(0L, on.amountIn(line, sep))
    }

    @Test
    fun amountIn_beforeStart_isZero_unshifted() {
        val line = salary(startMonth = oct)
        assertEquals(0L, off.amountIn(line, sep))
        assertEquals(100_000L, off.amountIn(line, oct))
    }

    @Test
    fun amountIn_usesTheVersionOfThePlanMonth_whenShifted() {
        val versions = listOf(AmountVersion(ym(2026, 1), 90_000L), AmountVersion(oct, 100_000L))
        val line = salary(versions = versions)
        assertEquals(90_000L, on.amountIn(line, sep))
        assertEquals(100_000L, on.amountIn(line, oct))
    }

    @Test
    fun incomeRowsIn_dropsInactiveLines() {
        val started = PlannedIncome("a", "A", 1_000L, 0, startMonth = oct)
        val always = PlannedIncome("b", "B", 2_000L, 1)
        assertEquals(listOf(2_000L), incomeRowsIn(listOf(started, always), sep, off).map { it.cents })
        assertEquals(listOf(1_000L, 2_000L), incomeRowsIn(listOf(started, always), oct, off).map { it.cents })
    }

    @Test
    fun plannedForCategory_budgetStartMonthAndVersions() {
        val versions = listOf(AmountVersion(sep, 20_000L), AmountVersion(oct, 30_000L))
        val budget = Budget(CategoryId("food"), 30_000L, startMonth = sep, versions = versions)
        assertEquals(PlannedAmount(null, PlannedSource.NONE), plannedForCategory(ym(2026, 8), budget, emptyList()))
        assertEquals(PlannedAmount(20_000L, PlannedSource.BUDGET), plannedForCategory(sep, budget, emptyList()))
        assertEquals(PlannedAmount(30_000L, PlannedSource.BUDGET), plannedForCategory(oct, budget, emptyList()))
    }

    private fun cat(
        id: String,
        group: String?,
    ) = Category(CategoryId(id), id, "icon", "slate", CategoryKind.SPENDING, 0, false, false, group)

    @Test
    fun envelopesOf_notYetStartedEnvelope_categoriesShowUnenveloped() {
        val dinners = cat("dinners", "env")
        val env = Envelope("env", "Food", 0, amountCents = 50_000L, startMonth = oct)
        val buckets = listOf(bucket(dinners, 10_000L, 2_000L))
        val lines = listOf(line(200_000L))
        val before = envelopesOf(buckets, listOf(dinners), listOf(env), lines, emptyList(), PlanMonth(sep, 20))
        assertTrue(before.envelopes.isEmpty())
        assertEquals(buckets, before.unenveloped)
        val after = envelopesOf(buckets, listOf(dinners), listOf(env), lines, emptyList(), PlanMonth(oct, 20))
        assertEquals(listOf("env"), after.envelopes.map { it.envelope.id })
        assertTrue(after.unenveloped.isEmpty())
    }

    @Test
    fun monthlyPlanOf_notYetStartedFixedEnvelope_isNotCounted() {
        val dinners = cat("dinners", "env")
        val env = Envelope("env", "Food", 0, amountCents = 50_000L, startMonth = oct)
        val buckets = listOf(bucket(dinners, 10_000L, 2_000L))
        val lines = listOf(line(200_000L))
        assertEquals(10_000L, monthlyPlanOf(buckets, lines, emptyList(), sep, 20, listOf(env)).plannedSpendingCents)
        assertEquals(50_000L, monthlyPlanOf(buckets, lines, emptyList(), oct, 20, listOf(env)).plannedSpendingCents)
    }

    @Test
    fun annualPlan_midYearAmountVersion_isSummedPerMonth() {
        val versions = listOf(AmountVersion(ym(2026, 1), 100_000L), AmountVersion(ym(2026, 7), 120_000L))
        val line = PlannedIncome("l", "L", 120_000L, 0, versions = versions)
        val plan = annualPlanOf(2026, planInputs(lines = listOf(line)))
        assertEquals(6 * 100_000L + 6 * 120_000L, plan.income.regularCents)
    }

    @Test
    fun annualPlan_midYearStartedEnvelope_countsOnlyItsMonths() {
        val dinners = planCategory("dinners", "food")
        val env = planEnvelope("food").copy(amountCents = 10_000L, startMonth = ym(PLAN_YEAR, 7))
        val result = annualPlanOf(PLAN_YEAR, planInputs(categories = listOf(dinners), envelopes = listOf(env)))
        assertEquals(6 * 10_000L, result.envelopes.first { it.envelope?.id == "food" }.plannedCents)
    }

    @Test
    fun annualPlan_envelopeAmountVersion_isPerMonthNotTimesTwelve() {
        val env =
            planEnvelope("food").copy(
                amountCents = 20_000L,
                versions = listOf(AmountVersion(ym(PLAN_YEAR, 1), 10_000L), AmountVersion(ym(PLAN_YEAR, 4), 20_000L)),
            )
        val result = annualPlanOf(PLAN_YEAR, planInputs(envelopes = listOf(env)))
        assertEquals(3 * 10_000L + 9 * 20_000L, result.envelopes.single().plannedCents)
    }

    @Test
    fun history_lineStartingMidRange_expectedOnlyFromItsStart() {
        val line = PlannedIncome("rent", "Rent", 20_000L, 0, startMonth = oct)
        val months = listOf(sep, oct, ym(2026, 11))
        val row = incomeHistoryOf(months, emptyList(), listOf(line), off, ZoneOffset.UTC, ym(2026, 11)).single()
        assertEquals(listOf(null, 20_000L, 20_000L), row.months.map { it.expectedCents })
    }

    private val profile =
        SalaryProfile(
            baseGrossCents = 100_000,
            irsBasePpm = 100_000,
            irsSubsidiesPpm = 50_000,
            mealRatePerDayCents = 900,
        )

    @Test
    fun sync_newLine_startsAtEffectiveFrom() {
        val sync = managedLinesSync(profile, emptyList(), effectiveFrom = ym(2026, 3))
        assertTrue(sync.upserts.isNotEmpty())
        assertTrue(sync.upserts.all { it.startMonth == ym(2026, 3) && it.versions.isEmpty() })
    }

    @Test
    fun sync_raiseAppendsVersion_repeatIsNoWrites_laterResyncKeepsRaise() {
        val jan = ym(2026, 1)
        val existing = managedLinesSync(profile, emptyList(), effectiveFrom = ym(2025, 6)).upserts
        val raised = profile.copy(baseGrossCents = 120_000)
        val sync = managedLinesSync(raised, existing, effectiveFrom = jan)
        val net = sync.upserts.single { it.id == MANAGED_NET_SALARY }
        assertEquals(ym(2025, 6), net.startMonth)
        assertEquals(jan, net.versions.last().from)
        assertEquals(net.amountCents, net.versions.last().amountCents)
        assertEquals(79_000L, net.versions.first().amountCents)

        val stored = existing.map { old -> sync.upserts.firstOrNull { it.id == old.id } ?: old }
        assertTrue(managedLinesSync(raised, stored, effectiveFrom = jan).isEmpty)
        // Resyncing later in the year with the same profile does not touch the January raise.
        assertTrue(managedLinesSync(raised, stored, effectiveFrom = ym(2026, 10)).isEmpty)
    }
}
