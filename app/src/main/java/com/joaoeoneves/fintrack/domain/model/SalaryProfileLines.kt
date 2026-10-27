package com.joaoeoneves.fintrack.domain.model

/** A managed line wanted by the profile, before the user's edits of an existing line are applied. */
private data class WantedLine(
    val key: String,
    val label: String,
    val amountCents: Long,
    val basis: IncomeBasis = IncomeBasis.FIXED,
    val frequency: IncomeFrequency = IncomeFrequency.YEARLY,
    val monthOfYear: Int? = null,
    val skipMonths: Set<Int> = emptySet(),
)

/** Whether this line was generated from the salary profile (its amount, basis and schedule are read-only). */
val PlannedIncome.isManaged: Boolean get() = managedKey != null

/**
 * The managed income lines [profile] produces, with the deterministic id = managedKey. A line whose input is
 * empty is not returned (so the caller removes it), and a line already in [existing] keeps the user's label,
 * earmark and order. The result is the same when applied again (idempotent). Empty when the profile's required
 * amount (base / net salary) is missing.
 */
fun salaryProfileLines(
    profile: SalaryProfile,
    existing: List<PlannedIncome>,
    labels: SalaryLineLabels = SalaryLineLabels(),
): List<PlannedIncome> {
    val breakdown = salaryBreakdownOf(profile) ?: return emptyList()
    val byKey = existing.filter { it.managedKey != null }.associateBy { it.managedKey }
    var nextOrder = (existing.maxOfOrNull { it.order } ?: -1) + 1
    return wantedLines(profile, breakdown, labels).filter { it.amountCents > 0L }.map { wanted ->
        val current = byKey[wanted.key]
        PlannedIncome(
            id = wanted.key,
            label = current?.label ?: wanted.label,
            amountCents = wanted.amountCents,
            order = current?.order ?: nextOrder++,
            basis = wanted.basis,
            frequency = wanted.frequency,
            monthOfYear = wanted.monthOfYear,
            skipMonths = wanted.skipMonths,
            earmark = current?.earmark,
            managedKey = wanted.key,
        )
    }
}

/** The ids of the managed lines in [existing] that [profile] no longer produces (to delete). */
fun staleManagedIds(
    profile: SalaryProfile?,
    existing: List<PlannedIncome>,
): List<String> {
    val keep = profile?.let { salaryProfileLines(it, existing) }.orEmpty().mapTo(mutableSetOf()) { it.id }
    return existing.filter { it.isManaged && it.id !in keep }.map { it.id }
}

private fun wantedLines(
    profile: SalaryProfile,
    breakdown: SalaryBreakdown,
    labels: SalaryLineLabels,
): List<WantedLine> {
    val firstSubsidyMonth = minOf(profile.holidaySubsidyMonth, profile.christmasSubsidyMonth)
    val secondSubsidyMonth = maxOf(profile.holidaySubsidyMonth, profile.christmasSubsidyMonth)
    return listOf(
        WantedLine(
            MANAGED_NET_SALARY,
            labels.netSalary,
            breakdown.netMonthlyCents,
            frequency = IncomeFrequency.MONTHLY,
        ),
        WantedLine(
            MANAGED_MEAL,
            labels.meal,
            breakdown.mealRatePerDayCents,
            basis = IncomeBasis.PER_WORKING_DAY,
            frequency = IncomeFrequency.MONTHLY,
        ),
        WantedLine(
            MANAGED_HOLIDAY,
            labels.subsidy,
            breakdown.holidayLumpNetCents,
            monthOfYear = firstSubsidyMonth,
        ),
        WantedLine(
            MANAGED_CHRISTMAS,
            labels.subsidy,
            breakdown.christmasLumpNetCents,
            monthOfYear = secondSubsidyMonth,
        ),
    ) + flexLines(profile, breakdown, labels) + profitShareLines(profile, breakdown, labels) +
        WantedLine(
            MANAGED_BONUS,
            labels.bonus,
            breakdown.bonusCents,
            monthOfYear = profile.bonusMonth ?: DEFAULT_EXTRA_PAY_MONTH,
        )
}

/** One profit-share line per month, the total split equally (floored) with the remainder on the last month. */
private fun profitShareLines(
    profile: SalaryProfile,
    breakdown: SalaryBreakdown,
    labels: SalaryLineLabels,
): List<WantedLine> {
    val months = profile.effectiveProfitShareMonths
    val share = breakdown.profitShareCents / months.size
    return months.mapIndexed { index, month ->
        val remainder = if (index == months.lastIndex) breakdown.profitShareCents - share * months.size else 0L
        WantedLine(managedProfitShareKey(month), labels.profitShare, share + remainder, monthOfYear = month)
    }
}

/** The four quarterly flexible-benefit lines, paid in the first month, +3, +6 and +9 (wrapping past December). */
private fun flexLines(
    profile: SalaryProfile,
    breakdown: SalaryBreakdown,
    labels: SalaryLineLabels,
): List<WantedLine> =
    breakdown.flexQuarterlyCents.mapIndexed { index, cents ->
        WantedLine(
            managedFlexKey(index),
            "${labels.flex} ${index + 1}/$FLEX_PAYMENTS",
            cents,
            monthOfYear = (profile.flexFirstMonth - 1 + index * FLEX_MONTH_STEP) % MONTHS_IN_YEAR + 1,
        )
    }

private const val FLEX_MONTH_STEP = 3
private const val MONTHS_IN_YEAR = 12
