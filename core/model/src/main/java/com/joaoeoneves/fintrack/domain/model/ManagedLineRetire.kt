package com.joaoeoneves.fintrack.domain.model

import java.time.YearMonth

/** Whether [line] already paid (or could have paid) in a month before [from], so removing it would rewrite them. */
internal fun PlannedIncome.hasHistoryBefore(from: YearMonth): Boolean = startMonth == null || startMonth < from

/** [line] ended from [from]: versioned to 0 there (unchanged when it already pays 0 from then on). */
internal fun ended(
    line: PlannedIncome,
    from: YearMonth,
): PlannedIncome = if (line.asOf(from)?.amountCents == 0L) line else line.editedFrom(from, 0L)

// A line the profile stopped paying: ended when it has history, else simply removed.
internal fun retireOrDelete(
    line: PlannedIncome,
    from: YearMonth,
    lines: MutableList<PlannedIncome>,
    deletes: MutableList<String>,
) {
    if (line.hasHistoryBefore(from)) lines += ended(line, from) else deletes += line.id
}
