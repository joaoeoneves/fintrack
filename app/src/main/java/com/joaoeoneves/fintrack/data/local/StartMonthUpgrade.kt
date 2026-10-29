package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.AmountVersion
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_VERSIONS
import java.time.YearMonth
import java.time.ZoneId

/** Schema version (guest file and backup) that introduced `startMonth` / `versions`. */
internal const val START_MONTH_SCHEMA_VERSION = 19

private val YEAR_MONTH_SHAPE = Regex("^[0-9]{4}-[0-9]{2}$")

/**
 * The 18 -> 19 upgrade: every budget, planned income line and envelope without a `startMonth` gets the earliest
 * month among the data set's expenses and income (the upgrade [now] month if there are none), so nothing that was
 * planned retroactively disappears from past months. Idempotent; the result carries [START_MONTH_SCHEMA_VERSION].
 */
internal fun LocalDataDocument.upgradedToStartMonths(
    now: YearMonth = YearMonth.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): LocalDataDocument {
    val start =
        (expenses.map { it.date } + income.map { it.date })
            .mapNotNull { parseDateOrNull(it, "upgrade", "-") }
            .minOrNull()
            ?.let { YearMonth.from(it.atZone(zone)) }
            ?: now
    val text = start.toString()
    return copy(
        schemaVersion = START_MONTH_SCHEMA_VERSION,
        budgets = budgets.map { if (it.startMonth == null) it.copy(startMonth = text) else it },
        plannedIncome = plannedIncome.map { if (it.startMonth == null) it.copy(startMonth = text) else it },
        categoryGroups = categoryGroups.map { if (it.startMonth == null) it.copy(startMonth = text) else it },
    )
}

/** Mirror of the firestore.rules check of `startMonth` and `versions`; throws [IllegalArgumentException]. */
internal fun validatePlanTiming(
    startMonth: String?,
    versions: List<LocalAmountVersion>,
) {
    require(startMonth == null || YEAR_MONTH_SHAPE.matches(startMonth)) { "Start month must be yyyy-MM" }
    require(versions.size <= MAX_AMOUNT_VERSIONS) { "At most $MAX_AMOUNT_VERSIONS amount versions" }
    versions.forEach {
        require(YEAR_MONTH_SHAPE.matches(it.from)) { "Version month must be yyyy-MM" }
        require(it.amountCents == null || it.amountCents in 0..MAX_AMOUNT_CENTS) {
            "Version amount must be 0..$MAX_AMOUNT_CENTS cents"
        }
    }
}

/** A whole stored budget: [validateBudget] plus its start month and versions. */
internal fun validateBudget(
    budget: LocalBudget,
    knownCategoryIds: Set<String>,
) {
    validateBudget(budget.categoryId, budget.limitCents, knownCategoryIds, budget.skipMonths)
    validatePlanTiming(budget.startMonth, budget.versions)
}

internal fun String?.toYearMonthOrNull(): YearMonth? = this?.let(::parseYearMonthOrNull)

/** Lenient: versions with an unparsable month are dropped. */
internal fun List<LocalAmountVersion>.toDomainVersions(): List<AmountVersion> =
    mapNotNull { v -> parseYearMonthOrNull(v.from)?.let { AmountVersion(it, v.amountCents) } }

internal fun List<AmountVersion>.toLocalVersions(): List<LocalAmountVersion> =
    map { LocalAmountVersion(it.from.toString(), it.amountCents) }
