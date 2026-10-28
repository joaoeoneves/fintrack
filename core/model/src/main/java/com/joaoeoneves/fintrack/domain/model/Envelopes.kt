package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** The month a plan is evaluated for: [month] and its [workingDays] (per-working-day lines multiply by it). */
data class PlanMonth(
    val month: YearMonth,
    val workingDays: Int,
)

/**
 * One envelope of a month: its category [rows] (every member category, whether or not it has something planned),
 * the month's [amountCents] (the fixed amount, else the sum of the rows' planned amounts, else `null` for "no
 * limit") and what the rows [spentCents]. [fundingCents] is what the [fundingLine] pays that month, `null` when the
 * envelope is unfunded.
 */
data class EnvelopeProgress(
    val envelope: Envelope,
    val amountCents: Long?,
    val spentCents: Long,
    val rows: List<CategoryBudget>,
    val fundingCents: Long? = null,
    val fundingLine: PlannedIncome? = null,
) {
    val isOver: Boolean get() = amountCents != null && spentCents > amountCents

    /** The larger of the amount (or 0) and the spend: what the envelope takes out of the month's money. */
    val committedCents: Long get() = maxOf(amountCents ?: 0L, spentCents)

    /** What its funding line leaves after the spend; `null` when unfunded. */
    val leftoverCents: Long? get() = fundingCents?.let { it - spentCents }

    /** How much of [committedCents] the funding line does not cover (taken from the free money); 0 when unfunded. */
    val overflowCents: Long get() = fundingCents?.let { maxOf(committedCents - it, 0L) } ?: 0L

    /** Whether part of the amount comes from recurring items rather than a budget. */
    val hasRecurringPlanned: Boolean get() = rows.any { it.plannedSource == PlannedSource.RECURRING }
}

/**
 * The month's envelopes in order, the [unenveloped] rows (no, or an unknown, envelope; only those with something
 * planned or spent) and [freeCents], the "Livre" money: [baseCents] (the income not set aside for a funded
 * envelope) minus [committedCents] (unfunded envelopes, unenveloped rows and the overflow of funded envelopes).
 */
data class EnvelopesSummary(
    val envelopes: List<EnvelopeProgress>,
    val unenveloped: List<CategoryBudget>,
    val freeCents: Long,
    val baseCents: Long = 0L,
    val committedCents: Long = 0L,
) {
    /** The funded envelopes whose commitment exceeds their funding. */
    val overflowing: List<EnvelopeProgress> get() = envelopes.filter { it.overflowCents > 0 }

    val unenvelopedPlannedCents: Long get() = unenveloped.sumOf { it.planned ?: 0L }
    val unenvelopedSpentCents: Long get() = unenveloped.sumOf { it.spentCents }
}

/** The buckets filed under each envelope (envelopes in order) and the ones filed under none. */
internal class BucketSplit(
    val byEnvelope: List<Pair<Envelope, List<CategoryBudget>>>,
    val loose: List<CategoryBudget>,
)

internal fun splitBuckets(
    buckets: List<CategoryBudget>,
    envelopes: List<Envelope>,
    groupIdOf: (CategoryBudget) -> String? = { it.category.groupId },
): BucketSplit {
    val sorted = envelopes.sortedWith(compareBy({ it.order }, { it.id }))
    val ids = sorted.mapTo(mutableSetOf()) { it.id }
    val byGroup = buckets.groupBy { groupIdOf(it)?.takeIf { id -> id in ids } }
    return BucketSplit(sorted.map { it to byGroup[it.id].orEmpty() }, byGroup[null].orEmpty())
}

/** An envelope's amount for a month: its fixed amount, else what its [rows] plan, else `null`. */
internal fun monthAmountOf(
    envelope: Envelope,
    rows: List<CategoryBudget>,
): Long? = envelope.amountCents ?: rows.mapNotNull { it.planned }.takeIf { it.isNotEmpty() }?.sum()

/**
 * Evaluates the envelopes for a month. An envelope's amount is its fixed amount, else the sum of its categories'
 * planned amounts; what it commits is the larger of that and its spend, pooled over its categories. An envelope
 * funded by a regular monthly income line (the first by order when several name the same line) is paid from that
 * line: [EnvelopesSummary.baseCents] is the larger of the expected regular income and the month's actual income,
 * minus the funding; only a funded envelope's overflow reduces it. Without funding the free money equals
 * [MonthlyPlanSummary.safeToSpendCents]. A funding line that is missing or is an extra counts as no funding.
 */
@Suppress("LongParameterList") // The plan's inputs, one per source.
fun envelopesOf(
    buckets: List<CategoryBudget>,
    categories: List<Category>,
    envelopes: List<Envelope>,
    plannedIncome: List<PlannedIncome>,
    monthIncome: List<Income>,
    planMonth: PlanMonth,
): EnvelopesSummary {
    val (month, workingDays) = planMonth
    val groupById = categories.associate { it.id to it.groupId }
    val split = splitBuckets(buckets, envelopes) { groupById[it.category.id] ?: it.category.groupId }
    val regular = plannedIncome.filterNot { it.isExtra }
    val lineById = regular.associateBy { it.id }
    val usedLines = mutableSetOf<String>()
    val progress =
        split.byEnvelope.map { (envelope, rows) ->
            val line = envelope.fundedByLineId?.let(lineById::get)?.takeIf { usedLines.add(it.id) }
            EnvelopeProgress(
                envelope = envelope,
                amountCents = monthAmountOf(envelope, rows),
                spentCents = rows.sumOf { it.spentCents },
                rows = rows,
                fundingCents = line?.amountIn(month, workingDays),
                fundingLine = line,
            )
        }
    val loose = split.loose.filter { it.planned != null || it.spentCents > 0 }
    val funding = progress.sumOf { it.fundingCents ?: 0L }
    val expected = regular.sumOf { it.amountIn(month, workingDays) }
    val actual = monthIncome.sumOf { it.amountCents }
    val base = maxOf(expected - funding, actual - funding)
    val committed =
        progress.filter { it.fundingCents == null }.sumOf { it.committedCents } +
            loose.sumOf { maxOf(it.planned ?: 0L, it.spentCents) } +
            progress.sumOf { it.overflowCents }
    return EnvelopesSummary(progress, loose, base - committed, base, committed)
}
