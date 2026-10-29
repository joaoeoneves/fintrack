package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.LocalIncome
import com.joaoeoneves.fintrack.data.local.LocalPlannedIncome
import com.joaoeoneves.fintrack.data.local.validateIncome
import com.joaoeoneves.fintrack.domain.model.parsePaydayIncomeId
import com.joaoeoneves.fintrack.domain.model.paydayIncomeId

/**
 * The valid local income to copy and how many are skipped. A payday income of a label-matched guest line moves to
 * the account's line; if that receipt already exists (or two guest lines collapse onto it) it is skipped and
 * counted, never a second salary.
 */
internal fun incomeToCopy(
    local: LocalDataDocument,
    remote: RemoteAccountSnapshot,
): Pair<List<LocalIncome>, Int> {
    val lineRemap = lineIdRemap(local.plannedIncome, remote)
    val taken = remote.incomeIds.toMutableSet()
    val income = mutableListOf<LocalIncome>()
    var skipped = 0
    for (original in local.income.filter { it.id !in remote.incomeIds }) {
        val item = original.remappedTo(lineRemap)
        if (isValid { validateIncome(item) } && taken.add(item.id)) income += item else skipped++
    }
    return income to skipped
}

/**
 * Guest line id to account line id for the guest lines not copied because the account has a line with the same
 * normalised label; their `pay_<line>_<yyyyMM>` incomes follow it (see [remappedTo]).
 */
private fun lineIdRemap(
    local: List<LocalPlannedIncome>,
    remote: RemoteAccountSnapshot,
): Map<String, String> =
    local
        .filter { it.id !in remote.plannedIncomeIds }
        .mapNotNull { line -> remote.plannedIncomeIdsByLabel[line.label.labelKey()]?.let { line.id to it } }
        .toMap()

private fun LocalIncome.remappedTo(lineRemap: Map<String, String>): LocalIncome {
    val parsed = parsePaydayIncomeId(id)
    val target = parsed?.let { lineRemap[it.first] }
    return if (parsed != null && target != null) copy(id = paydayIncomeId(target, parsed.second)) else this
}
