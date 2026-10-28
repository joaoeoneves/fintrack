package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * The writes that fold recurring income into planned income lines: [newLines] (one `rinc_<itemId>` per active
 * recurring income), [autoRecordLineIds] (existing lines the legacy payday switch covered, to save with
 * `autoRecord = true`), the line [cursors] to set, the recurring income items to delete ([deleteRecurringIds]; their
 * recorded incomes stay) and whether to switch the legacy payday flag off ([clearLegacyPayday]). Empty once migrated.
 */
data class IncomeMigration(
    val newLines: List<PlannedIncome>,
    val autoRecordLineIds: Set<String>,
    val cursors: Map<String, YearMonth>,
    val deleteRecurringIds: List<String>,
    val clearLegacyPayday: Boolean,
) {
    val isEmpty: Boolean
        get() =
            newLines.isEmpty() &&
                autoRecordLineIds.isEmpty() &&
                cursors.isEmpty() &&
                deleteRecurringIds.isEmpty() &&
                !clearLegacyPayday
}

/**
 * Computes the [IncomeMigration]. An active, not ended recurring INCOME item becomes the line
 * `rinc_<itemId>` (fixed amount, monthly or yearly, paid on the item's day, recording itself when the item
 * auto-posted) unless that line exists; its cursor starts after the item's last handled occurrence. Every recurring
 * income item, in any state, is deleted. When the legacy payday switch is on and there are lines, every line not yet
 * recording itself takes it over with the legacy cursor; with no lines (maybe a partial cache) nothing happens.
 */
fun incomeMigrationOf(
    recurring: List<RecurringItem>,
    lines: List<PlannedIncome>,
    settings: AutomationSettings,
    today: LocalDate,
): IncomeMigration {
    val items = recurring.filter { it.type == RecurringType.INCOME }.sortedBy { it.id }
    val byId = lines.associateBy { it.id }
    var nextOrder = (lines.maxOfOrNull { it.order } ?: -1) + 1
    val live = items.filter { it.active && !it.isEnded(today) }
    val newLines = live.filter { recurringIncomeLineId(it.id) !in byId }.map { it.toIncomeLine(nextOrder++) }
    val cursors = mutableMapOf<String, YearMonth>()
    live.filter { it.autoPost }.forEach { item ->
        val id = recurringIncomeLineId(item.id)
        val line = newLines.firstOrNull { it.id == id } ?: byId[id]?.takeIf { it.autoRecord }
        if (line != null && id !in settings.lineRecordedThrough) {
            cursors[id] = item.handledThrough?.let { YearMonth.from(it) } ?: initialLineCursor(line, today)
        }
    }
    val switchLegacy = settings.legacyPaydayEnabled && lines.isNotEmpty()
    val takenOver = if (switchLegacy) lines.filter { !it.autoRecord } else emptyList()
    takenOver.filter { it.id !in settings.lineRecordedThrough && it.id !in cursors }.forEach {
        cursors[it.id] = settings.legacyPaydayRecordedThrough ?: initialLineCursor(it, today)
    }
    return IncomeMigration(
        newLines = newLines,
        autoRecordLineIds = takenOver.mapTo(linkedSetOf()) { it.id },
        cursors = cursors,
        deleteRecurringIds = items.map { it.id },
        clearLegacyPayday = switchLegacy,
    )
}

private fun RecurringItem.toIncomeLine(order: Int): PlannedIncome =
    PlannedIncome(
        id = recurringIncomeLineId(id),
        label = name.trim().take(MAX_PLAN_LABEL_LENGTH),
        amountCents = amountCents,
        order = order,
        basis = IncomeBasis.FIXED,
        frequency = if (frequency == RecurringFrequency.YEARLY) IncomeFrequency.YEARLY else IncomeFrequency.MONTHLY,
        monthOfYear = monthOfYear.takeIf { frequency == RecurringFrequency.YEARLY },
        autoRecord = autoPost,
        payDay = dayOfMonth,
    )

private val SALARY_WORDS = listOf("salario", "ordenado", "vencimento", "salary", "wage", "paycheck")
private const val SALARY_TOLERANCE_PERCENT = 10L
private const val PERCENT = 100L

/**
 * Whether this manual monthly line looks like a duplicate of the salary the profile already generates: a managed
 * net-salary line exists among [lines] and the label names a salary or the amount is within 10% of it.
 */
fun PlannedIncome.looksLikeSalary(lines: List<PlannedIncome>): Boolean {
    val salary = lines.firstOrNull { it.managedKey == MANAGED_NET_SALARY }
    if (isManaged || frequency != IncomeFrequency.MONTHLY || salary == null) return false
    val normalized = normalizedMerchantName(label)
    val tolerance = SALARY_TOLERANCE_PERCENT * salary.amountCents
    val near = salary.amountCents > 0L && (amountCents - salary.amountCents) * PERCENT in -tolerance..tolerance
    return near || SALARY_WORDS.any { it in normalized }
}
