package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** The user-facing income components the salary profile's managed lines are grouped into, in display order. */
enum class SalaryComponent { SALARY, MEAL, SUBSIDIES, EXTRA }

/** The component of a managed line (the net salary, meal, subsidies, ...); `null` for a manual line. */
val PlannedIncome.salaryComponent: SalaryComponent?
    get() =
        when (val key = managedKey) {
            null -> null
            MANAGED_NET_SALARY -> SalaryComponent.SALARY
            MANAGED_MEAL -> SalaryComponent.MEAL
            MANAGED_HOLIDAY, MANAGED_CHRISTMAS, MANAGED_DUODECIMOS -> SalaryComponent.SUBSIDIES
            else -> if (isValidManagedKey(key)) SalaryComponent.EXTRA else null
        }

/**
 * The id of the extra a managed line belongs to; the legacy flex / profit-share / bonus lines map to the ids their
 * migrated extras get, so they group with them until the next sync deletes them. `null` for any other line.
 */
val PlannedIncome.salaryExtraId: String?
    get() =
        when (val key = managedKey) {
            null -> null
            MANAGED_BONUS -> LEGACY_BONUS_ID
            else ->
                parseManagedExtraKey(key)?.first
                    ?: LEGACY_PROFIT_SHARE_ID.takeIf { key.startsWith(MANAGED_PROFIT_SHARE) }
                    ?: LEGACY_FLEX_ID.takeIf { key.startsWith("profile_flex_q") }
        }

/**
 * One row of the income lists: every line of a [component] added up, or a single manual line (`component == null`).
 * [cents] is 0 for "not this month"; [months] (1..12) are the months paying something, filled for a whole year.
 */
data class IncomeRow(
    val component: SalaryComponent?,
    val lines: List<PlannedIncome>,
    val cents: Long,
    val months: List<Int> = emptyList(),
    /** The extra an [SalaryComponent.EXTRA] row stands for. */
    val extraId: String? = null,
)

private fun List<PlannedIncome>.inDisplayOrder(): List<PlannedIncome> = sortedWith(compareBy({ it.order }, { it.id }))

private fun rowsOf(
    lines: List<PlannedIncome>,
    build: (component: SalaryComponent?, extraId: String?, lines: List<PlannedIncome>) -> IncomeRow,
): List<IncomeRow> {
    val sorted = lines.inDisplayOrder()
    val managed = sorted.filter { it.isManaged }.groupBy { it.salaryComponent to it.salaryExtraId }
    // Fixed components in enum order; extras follow, ordered by their first line.
    val rows =
        managed.entries
            .sortedWith(compareBy({ it.key.first?.ordinal }, { it.value.first().order }, { it.value.first().id }))
            .map { (key, group) -> build(key.first, key.second, group) }
    return rows + sorted.filterNot { it.isManaged }.map { build(null, null, listOf(it)) }
}

/** The month's income rows: managed components first (enum order), then each manual line. */
fun incomeRowsIn(
    lines: List<PlannedIncome>,
    month: YearMonth,
    workingDays: Int,
): List<IncomeRow> =
    rowsOf(lines) { component, extraId, group ->
        IncomeRow(component, group, group.sumOf { it.amountIn(month, workingDays) }, extraId = extraId)
    }

/** The year's income rows like [incomeRowsIn], with the year's total and paying months; empty rows are dropped. */
fun annualIncomeRowsOf(
    lines: List<PlannedIncome>,
    workingDays: Map<YearMonth, Int>,
): List<IncomeRow> =
    rowsOf(lines) { component, extraId, group ->
        val byMonth =
            workingDays.entries
                .sortedBy { it.key }
                .map { (month, days) -> month.monthValue to group.sumOf { it.amountIn(month, days) } }
        val paying = byMonth.filter { it.second > 0 }.map { it.first }
        IncomeRow(component, group, byMonth.sumOf { it.second }, paying, extraId)
    }.filter { it.cents > 0 }
