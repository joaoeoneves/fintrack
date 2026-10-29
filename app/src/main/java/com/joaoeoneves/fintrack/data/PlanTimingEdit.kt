package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.editedFrom
import com.joaoeoneves.fintrack.domain.model.startingAt
import java.time.YearMonth

/** The budget to store when [limitCents] applies from [from]: a new one starts there, an existing one is versioned. */
internal fun Budget?.savedAs(
    categoryId: CategoryId,
    limitCents: Long,
    skipMonths: Set<Int>,
    from: YearMonth,
): Budget =
    (this?.copy(skipMonths = skipMonths) ?: Budget(categoryId, limitCents, skipMonths).startingAt(from))
        .editedFrom(from, limitCents)

/** The envelope to store when its fixed [amountCents] applies from [from]: a new one starts there. */
internal fun Envelope?.savedAs(
    template: Envelope,
    amountCents: Long?,
    from: YearMonth,
): Envelope =
    (this?.copy(name = template.name, fundedByLineId = template.fundedByLineId) ?: template.startingAt(from))
        .editedFrom(from, amountCents)
