package com.joaoeoneves.fintrack.data.local

import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_CENTS
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH

private val LINE_ID_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")

internal fun LocalCategoryGroup.toDomain(): Envelope =
    Envelope(
        id = id,
        name = name,
        order = order,
        amountCents = amountCents,
        fundedByLineId = fundedByLineId,
        startMonth = startMonth.toYearMonthOrNull(),
        versions = versions.toDomainVersions(),
    )

internal fun Envelope.toLocal(): LocalCategoryGroup =
    LocalCategoryGroup(
        id = id,
        name = name,
        order = order,
        amountCents = amountCents,
        fundedByLineId = fundedByLineId,
        startMonth = startMonth?.toString(),
        versions = versions.toLocalVersions(),
    )

/** Mirror of the firestore.rules check; throws [IllegalArgumentException] for a violation. */
internal fun validateCategoryGroup(group: LocalCategoryGroup) {
    requireCategoryIdShape(group.id)
    requireText(group.name, MAX_GROUP_NAME_LENGTH, "Envelope name")
    group.amountCents?.let {
        require(it in 1..MAX_AMOUNT_CENTS) { "Envelope amount must be 1..$MAX_AMOUNT_CENTS cents" }
    }
    validatePlanTiming(group.startMonth, group.versions)
    group.fundedByLineId?.let { require(LINE_ID_REGEX.matches(it)) { "Envelope funding line id is malformed" } }
}
