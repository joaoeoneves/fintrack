package com.joaoeoneves.fintrack.domain.model

/** The set-up steps of Home's "Get started" checklist, in display order. */
enum class StartStep { SALARY, ENVELOPES, BILLS, SAVINGS_PLAN, PAYDAY, FIRST_EXPENSE }

/** Which [StartStep]s are [done]. */
data class StartChecklist(
    val done: Set<StartStep>,
) {
    val doneCount: Int get() = done.size
    val total: Int get() = StartStep.entries.size
    val isComplete: Boolean get() = doneCount >= total
}

/**
 * Derives the checklist from the user's data: a salary profile or any income line, any envelope, any active
 * bill (an expense recurring item that is not a savings plan's own contribution), any non-archived savings
 * goal, any income line recorded automatically on its pay day, and at least one expense ([hasExpense]).
 */
@Suppress("LongParameterList") // One input per data source the steps read.
fun startChecklistOf(
    profile: SalaryProfile?,
    lines: List<PlannedIncome>,
    envelopes: List<Envelope>,
    recurring: List<RecurringItem>,
    goals: List<SavingsGoal>,
    hasExpense: Boolean,
): StartChecklist {
    val planRecurringIds = goals.mapNotNullTo(mutableSetOf()) { it.recurringId }
    val done =
        buildSet {
            if (profile != null || lines.isNotEmpty()) add(StartStep.SALARY)
            if (envelopes.isNotEmpty()) add(StartStep.ENVELOPES)
            if (recurring.any { it.type == RecurringType.EXPENSE && it.active && it.id !in planRecurringIds }) {
                add(StartStep.BILLS)
            }
            if (goals.any { !it.archived }) add(StartStep.SAVINGS_PLAN)
            if (lines.any { it.autoRecord }) add(StartStep.PAYDAY)
            if (hasExpense) add(StartStep.FIRST_EXPENSE)
        }
    return StartChecklist(done)
}
