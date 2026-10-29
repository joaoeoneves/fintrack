package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** The user-facing income components the salary profile's managed lines are grouped into, in display order. */
enum class SalaryComponent { SALARY, MEAL, SUBSIDIES, HOLIDAY, CHRISTMAS, EXTRA }

/** The component of a managed line (the net salary, meal, subsidies, ...); `null` for a manual line. */
val PlannedIncome.salaryComponent: SalaryComponent?
    get() =
        when (val key = managedKey?.let(::baseManagedKey)) {
            null -> null
            MANAGED_NET_SALARY -> SalaryComponent.SALARY
            MANAGED_MEAL -> SalaryComponent.MEAL
            MANAGED_DUODECIMOS -> SalaryComponent.SUBSIDIES
            MANAGED_HOLIDAY -> SalaryComponent.HOLIDAY
            MANAGED_CHRISTMAS -> SalaryComponent.CHRISTMAS
            else -> if (isValidManagedKey(key)) SalaryComponent.EXTRA else null
        }

/** The id of the extra a managed line belongs to; `null` for any other line. */
val PlannedIncome.salaryExtraId: String? get() = managedKey?.let { parseManagedExtraKey(it)?.first }

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

internal fun rowsOf(
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
): List<IncomeRow> = incomeRowsIn(lines, month, IncomeMonth.forMonth(month, workingDays))
