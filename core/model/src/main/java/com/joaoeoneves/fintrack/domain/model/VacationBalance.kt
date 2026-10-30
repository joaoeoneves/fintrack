package com.joaoeoneves.fintrack.domain.model

/** The vacation days of a calendar year: [used] (recorded as vacation in the months) out of [total] allowed. */
data class VacationBalance(
    val used: Int,
    val total: Int,
) {
    /** Vacation days not yet taken, never negative. */
    val remaining: Int get() = maxOf(0, total - used)

    /** Vacation days taken beyond the allowance, 0 when within it. */
    val overBy: Int get() = maxOf(0, used - total)
}

/** The [year]'s balance: the vacation days recorded in [settings] against the profile's days a year (default 22). */
fun vacationBalanceOf(
    year: Int,
    settings: List<MonthSettings>,
    profile: SalaryProfile?,
): VacationBalance =
    VacationBalance(
        used = settings.filter { it.month.year == year }.sumOf { it.vacationDays },
        total = profile.vacationDays,
    )
