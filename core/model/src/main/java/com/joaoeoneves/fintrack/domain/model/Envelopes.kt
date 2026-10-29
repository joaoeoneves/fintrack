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

    /** The money this envelope takes out of the month: its funding plus overflow when funded, else [committedCents]. */
    val reservedCents: Long get() = fundingCents?.let { it + overflowCents } ?: committedCents

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
    /** The part of [committedCents] set aside in SAVING-kind categories (not part of any envelope). */
    val savingCommittedCents: Long = 0L,
    /** The month's income basis: the larger of the expected regular income and what was actually received. */
    val incomeCents: Long = 0L,
    /**
     * The part of the money set aside for envelopes and loose categories that is recurring bills (each unit's
     * recurring planned-or-spent amount, capped at what the unit reserves), so "recurring" and "envelopes" never
     * count the same euro twice. See [MonthFigures].
     */
    val recurringCents: Long = 0L,
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
 * Evaluates the envelope groups for a month: per group what its categories planned and spent (no funding, no free
 * money; income is not part of the groups). SAVING-kind categories belong to no group: they only add what they
 * commit (the larger of planned and spent) to [EnvelopesSummary.savingCommittedCents].
 */
fun envelopesOf(
    buckets: List<CategoryBudget>,
    categories: List<Category>,
    envelopes: List<Envelope>,
    planMonth: PlanMonth,
): EnvelopesSummary {
    val groupById = categories.associate { it.id to it.groupId }
    val kindById = categories.associate { it.id to it.kind }
    val (saving, others) = buckets.partition { (kindById[it.category.id] ?: it.category.kind) == CategoryKind.SAVING }
    val split =
        splitBuckets(others, envelopes.asOf(planMonth.month)) { groupById[it.category.id] ?: it.category.groupId }
    val progress =
        split.byEnvelope.map { (envelope, rows) ->
            EnvelopeProgress(
                envelope = envelope,
                amountCents = monthAmountOf(envelope, rows),
                spentCents = rows.sumOf { it.spentCents },
                rows = rows,
            )
        }
    return EnvelopesSummary(
        envelopes = progress,
        unenveloped = split.loose.filter { it.planned != null || it.spentCents > 0 },
        freeCents = 0L,
        savingCommittedCents = saving.sumOf { maxOf(it.planned ?: 0L, it.spentCents) },
    )
}
