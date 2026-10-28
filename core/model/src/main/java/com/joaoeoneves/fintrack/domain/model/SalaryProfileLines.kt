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
    val salaryEarmark = byKey[MANAGED_NET_SALARY]?.earmark
    var nextOrder = (existing.maxOfOrNull { it.order } ?: -1) + 1
    return wantedLines(profile, breakdown, labels).filter { it.amountCents > 0L }.map { wanted ->
        val current = byKey[wanted.key]
        PlannedIncome(
            id = wanted.key,
            label = wanted.label.takeIf { parseManagedExtraKey(wanted.key) != null } ?: current?.label ?: wanted.label,
            amountCents = wanted.amountCents,
            order = current?.order ?: nextOrder++,
            basis = wanted.basis,
            frequency = wanted.frequency,
            monthOfYear = wanted.monthOfYear,
            skipMonths = wanted.skipMonths,
            earmark = if (wanted.key == MANAGED_DUODECIMOS) salaryEarmark else current?.earmark,
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

/** The writes that bring the stored managed lines in line with the profile: [upserts] (new first) and [deletes]. */
data class ManagedLinesSync(
    val upserts: List<PlannedIncome>,
    val deletes: List<String>,
) {
    val isEmpty: Boolean get() = upserts.isEmpty() && deletes.isEmpty()
}

/** The minimal writes that make the managed lines in [existing] match [profile]; empty when they already do. */
fun managedLinesSync(
    profile: SalaryProfile,
    existing: List<PlannedIncome>,
    labels: SalaryLineLabels = SalaryLineLabels(),
): ManagedLinesSync {
    val byId = existing.associateBy { it.id }
    val (changed, created) =
        salaryProfileLines(profile, existing, labels)
            .filter { byId[it.id] != it }
            .partition { it.id in byId }
    return ManagedLinesSync(upserts = created + changed, deletes = staleManagedIds(profile, existing))
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
            breakdown.netBaseCents,
            frequency = IncomeFrequency.MONTHLY,
        ),
        WantedLine(
            MANAGED_DUODECIMOS,
            labels.duodecimos,
            breakdown.netDuoCents,
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
    ) + extraLines(breakdown)
}

/** One line per payment of each extra, named after the extra so a rename follows through. */
private fun extraLines(breakdown: SalaryBreakdown): List<WantedLine> =
    breakdown.extras.flatMap { extra ->
        extra.payments.map { (month, cents) ->
            WantedLine(managedExtraKey(extra.extraId, month), extra.name, cents, monthOfYear = month)
        }
    }
