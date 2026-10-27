package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.MAX_PLAN_LABEL_LENGTH
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.codePointLength

internal fun LocalPlannedIncome.toDomain(): PlannedIncome =
    PlannedIncome(
        id = id,
        label = label,
        amountCents = amountCents,
        order = order,
    )

internal fun PlannedIncome.toLocal(): LocalPlannedIncome =
    LocalPlannedIncome(id = id, label = label, amountCents = amountCents, order = order)

/** Mirror of the firestore.rules check; throws [IllegalArgumentException] for a violation. */
internal fun validatePlannedIncome(line: LocalPlannedIncome) {
    require(line.id.isNotBlank()) { "Planned income id is blank" }
    require(line.label.isNotBlank() && line.label.codePointLength() <= MAX_PLAN_LABEL_LENGTH) {
        "Planned income label must be 1..$MAX_PLAN_LABEL_LENGTH characters"
    }
    require(line.amountCents > 0) { "Planned income amount must be positive" }
}
