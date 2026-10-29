package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

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
 * The managed income lines [profile] produces, with the deterministic id = managedKey. A line already in [existing]
 * keeps the user's label and order (a label that is still a default of some language, see [knownDefaults], is
 * replaced by the wanted one). The result is the same when applied again (idempotent). Empty when the profile's
 * required amount (base / net salary) is missing.
 *
 * A line already in [existing] keeps its start month and amount history; when the profile's amount differs from
 * the line's latest one, a version with the new amount is added from [effectiveFrom] (a raise from January). A new
 * line starts at [effectiveFrom].
 *
 * A line the profile no longer pays (amount 0 or an extra gone) that has history is not dropped: it is versioned to
 * 0 from [effectiveFrom] and stays in the result, so earlier months keep what it paid (see [staleManagedIds] for
 * the ones without history). A subsidy whose month of year changed keeps the old line (ended from [effectiveFrom])
 * and adds a new one under [scheduledManagedKey] that carries the new schedule from [effectiveFrom].
 */
fun salaryProfileLines(
    profile: SalaryProfile,
    existing: List<PlannedIncome>,
    labels: SalaryLineLabels = SalaryLineLabels(),
    knownDefaults: List<SalaryLineLabels> = emptyList(),
    effectiveFrom: YearMonth = YearMonth.now(),
): List<PlannedIncome> = managedPlanOf(profile, existing, labels, knownDefaults, effectiveFrom).lines

/**
 * The ids of the managed lines in [existing] to delete: all of them when [profile] is null or has no base amount,
 * else those it no longer pays that have no history before [effectiveFrom] (the others are ended instead, see
 * [salaryProfileLines]).
 */
fun staleManagedIds(
    profile: SalaryProfile?,
    existing: List<PlannedIncome>,
    effectiveFrom: YearMonth = YearMonth.now(),
): List<String> =
    if (profile == null) {
        existing.filter { it.isManaged }.map { it.id }
    } else {
        managedPlanOf(profile, existing, SalaryLineLabels(), emptyList(), effectiveFrom).deletes
    }

private class ManagedPlan(
    val lines: List<PlannedIncome>,
    val deletes: List<String>,
)

// One pass over the wanted lines; splitting it would scatter the rules.
@Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
private fun managedPlanOf(
    profile: SalaryProfile,
    existing: List<PlannedIncome>,
    labels: SalaryLineLabels,
    knownDefaults: List<SalaryLineLabels>,
    effectiveFrom: YearMonth,
): ManagedPlan {
    val managed = existing.filter { it.managedKey != null }
    val breakdown = salaryBreakdownOf(profile) ?: return ManagedPlan(emptyList(), managed.map { it.id })
    // Per fixed key the line in force: the one that started last (a scheduled subsidy replaces the older ones).
    val currents =
        managed
            .groupBy { baseManagedKey(it.managedKey.orEmpty()) }
            .mapValues { (_, group) -> group.maxBy { it.startMonth ?: EARLIEST_MONTH } }
    val salaryLine = currents[MANAGED_NET_SALARY]
    val duplicates =
        managed.filter { line ->
            line.id != line.managedKey &&
                line.id != currents[baseManagedKey(line.managedKey.orEmpty())]?.id
        }
    var nextOrder = (existing.maxOfOrNull { it.order } ?: -1) + 1
    val wantedAll = wantedLines(profile, breakdown, labels)
    val lines = mutableListOf<PlannedIncome>()
    val deletes = mutableListOf<String>()
    for (wanted in wantedAll) {
        val current = currents[wanted.key]
        if (wanted.amountCents <= 0L) {
            current?.let { retireOrDelete(it, effectiveFrom, lines, deletes) }
            continue
        }
        val scheduleChanged =
            current != null && (wanted.monthOfYear != current.monthOfYear || wanted.skipMonths != current.skipMonths)
        val line =
            PlannedIncome(
                id = current?.id ?: wanted.key,
                label = keptLabel(wanted, current, knownDefaults),
                amountCents = current?.amountCents ?: wanted.amountCents,
                order = current?.order ?: nextOrder++,
                basis = wanted.basis,
                frequency = wanted.frequency,
                monthOfYear = wanted.monthOfYear,
                skipMonths = wanted.skipMonths,
                managedKey = current?.managedKey ?: wanted.key,
                autoRecord = (current ?: salaryLine)?.autoRecord ?: false,
                payDay = (current ?: salaryLine)?.payDay,
                payDayEarlierIfClosed = (current ?: salaryLine)?.payDayEarlierIfClosed ?: false,
                startMonth = current?.startMonth,
                versions = current?.versions.orEmpty(),
            )
        when {
            current == null -> lines += line.startingAt(effectiveFrom)
            scheduleChanged && current.hasHistoryBefore(effectiveFrom) -> {
                // The old line keeps its schedule for the months before; a new one carries the new schedule on.
                lines += ended(current, effectiveFrom)
                val key = scheduledManagedKey(wanted.key, effectiveFrom)
                lines +=
                    line
                        .copy(id = key, managedKey = key, amountCents = wanted.amountCents, versions = emptyList())
                        .startingAt(effectiveFrom)
            }
            current.amountCents != wanted.amountCents -> lines += line.editedFrom(effectiveFrom, wanted.amountCents)
            else -> lines += line
        }
    }
    // Lines of a key the profile does not produce at all (an extra removed or moved to another month).
    val wantedKeys = wantedAll.mapTo(mutableSetOf()) { it.key }
    currents.filterKeys { it !in wantedKeys }.values.forEach { retireOrDelete(it, effectiveFrom, lines, deletes) }
    // A second line of a key whose id is not the deterministic one (the current line and the scheduled ones are).
    duplicates.forEach { retireOrDelete(it, effectiveFrom, lines, deletes) }
    return ManagedPlan(lines, deletes)
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
    knownDefaults: List<SalaryLineLabels> = emptyList(),
    effectiveFrom: YearMonth = YearMonth.now(),
): ManagedLinesSync {
    val byId = existing.associateBy { it.id }
    val (changed, created) =
        salaryProfileLines(profile, existing, labels, knownDefaults, effectiveFrom)
            .filter { byId[it.id] != it }
            .partition { it.id in byId }
    return ManagedLinesSync(upserts = created + changed, deletes = staleManagedIds(profile, existing, effectiveFrom))
}

/** The existing line's own label, unless it is an extra (named after the extra) or still an untouched default. */
private fun keptLabel(
    wanted: WantedLine,
    current: PlannedIncome?,
    knownDefaults: List<SalaryLineLabels>,
): String =
    when {
        parseManagedExtraKey(wanted.key) != null || current == null -> wanted.label
        knownDefaults.any { it.labelFor(wanted.key) == current.label } -> wanted.label
        else -> current.label
    }

/** The label of the fixed managed line [managedKey]; `null` for an extra's line (named after the extra) or unknown. */
fun SalaryLineLabels.labelFor(managedKey: String): String? =
    when (baseManagedKey(managedKey)) {
        MANAGED_NET_SALARY -> netSalary
        MANAGED_DUODECIMOS -> duodecimos
        MANAGED_MEAL -> meal
        MANAGED_HOLIDAY -> holidaySubsidy
        MANAGED_CHRISTMAS -> christmasSubsidy
        else -> null
    }

private fun wantedLines(
    profile: SalaryProfile,
    breakdown: SalaryBreakdown,
    labels: SalaryLineLabels,
): List<WantedLine> =
    listOf(
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
            labels.holidaySubsidy,
            breakdown.holidayLumpNetCents,
            monthOfYear = profile.holidaySubsidyMonth,
        ),
        WantedLine(
            MANAGED_CHRISTMAS,
            labels.christmasSubsidy,
            breakdown.christmasLumpNetCents,
            monthOfYear = profile.christmasSubsidyMonth,
        ),
    ) + extraLines(breakdown)

/** One line per payment of each extra, named after the extra so a rename follows through. */
private fun extraLines(breakdown: SalaryBreakdown): List<WantedLine> =
    breakdown.extras.flatMap { extra ->
        extra.payments.map { (month, cents) ->
            WantedLine(managedExtraKey(extra.extraId, month), extra.name, cents, monthOfYear = month)
        }
    }
