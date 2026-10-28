package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** The user-facing income components the salary profile's managed lines are grouped into, in display order. */
enum class SalaryComponent { SALARY, MEAL, SUBSIDIES, FLEX, PROFIT_SHARE, BONUS }

/** The component of a managed line (the net salary, meal, subsidies, ...); `null` for a manual line. */
val PlannedIncome.salaryComponent: SalaryComponent?
    get() =
        when (val key = managedKey) {
            null -> null
            MANAGED_NET_SALARY -> SalaryComponent.SALARY
            MANAGED_MEAL -> SalaryComponent.MEAL
            MANAGED_HOLIDAY, MANAGED_CHRISTMAS, MANAGED_DUODECIMOS -> SalaryComponent.SUBSIDIES
            MANAGED_BONUS -> SalaryComponent.BONUS
            else ->
                when {
                    key.startsWith(MANAGED_PROFIT_SHARE) -> SalaryComponent.PROFIT_SHARE
                    key.startsWith("profile_flex_q") -> SalaryComponent.FLEX
                    else -> null
                }
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
)

private fun List<PlannedIncome>.inDisplayOrder(): List<PlannedIncome> = sortedWith(compareBy({ it.order }, { it.id }))

private fun rowsOf(
    lines: List<PlannedIncome>,
    build: (component: SalaryComponent?, lines: List<PlannedIncome>) -> IncomeRow,
): List<IncomeRow> {
    val sorted = lines.inDisplayOrder()
    val managed = sorted.filter { it.isManaged }.groupBy { it.salaryComponent }
    return SalaryComponent.entries.mapNotNull { component -> managed[component]?.let { build(component, it) } } +
        sorted.filterNot { it.isManaged }.map { build(null, listOf(it)) }
}

/** The month's income rows: managed components first (enum order), then each manual line. */
fun incomeRowsIn(
    lines: List<PlannedIncome>,
    month: YearMonth,
    workingDays: Int,
): List<IncomeRow> =
    rowsOf(lines) { component, group ->
        IncomeRow(component, group, group.sumOf { it.amountIn(month, workingDays) })
    }

/** The year's income rows like [incomeRowsIn], with the year's total and paying months; empty rows are dropped. */
fun annualIncomeRowsOf(
    lines: List<PlannedIncome>,
    workingDays: Map<YearMonth, Int>,
): List<IncomeRow> =
    rowsOf(lines) { component, group ->
        val byMonth =
            workingDays.entries
                .sortedBy { it.key }
                .map { (month, days) -> month.monthValue to group.sumOf { it.amountIn(month, days) } }
        IncomeRow(component, group, byMonth.sumOf { it.second }, byMonth.filter { it.second > 0 }.map { it.first })
    }.filter { it.cents > 0 }
