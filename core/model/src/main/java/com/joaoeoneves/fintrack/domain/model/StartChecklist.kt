package com.joaoeoneves.fintrack.domain.model

/** The set-up steps of Home's "Get started" checklist, in display order. */
enum class StartStep { SALARY, BILLS, PAYDAY, FIRST_EXPENSE }

/** Which [StartStep]s are [done]. */
data class StartChecklist(
    val done: Set<StartStep>,
) {
    val doneCount: Int get() = done.size
    val total: Int get() = StartStep.entries.size
    val isComplete: Boolean get() = doneCount >= total
}

/**
 * Derives the checklist from the user's data: a salary profile or any income line, any active bill (an expense
 * recurring item), any income line recorded automatically on its pay day, and at least one expense ([hasExpense]).
 */
fun startChecklistOf(
    profile: SalaryProfile?,
    lines: List<PlannedIncome>,
    recurring: List<RecurringItem>,
    hasExpense: Boolean,
): StartChecklist {
    val done =
        buildSet {
            if (profile != null || lines.isNotEmpty()) add(StartStep.SALARY)
            if (recurring.any { it.type == RecurringType.EXPENSE && it.active }) add(StartStep.BILLS)
            if (lines.any { it.autoRecord }) add(StartStep.PAYDAY)
            if (hasExpense) add(StartStep.FIRST_EXPENSE)
        }
    return StartChecklist(done)
}
