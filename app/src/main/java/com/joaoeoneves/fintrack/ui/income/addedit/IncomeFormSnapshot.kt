package com.joaoeoneves.fintrack.ui.income.addedit

import kotlinx.serialization.Serializable
import java.time.Instant

/** The fields of the income form that survive process death. */
@Serializable
data class IncomeFormSnapshot(
    val incomeId: String?,
    val source: String,
    val amountCents: Long,
    val dateEpochMillis: Long,
    val note: String,
)

internal fun IncomeFormState.toSnapshot() =
    IncomeFormSnapshot(incomeId, source, amountCents, date.toEpochMilli(), note)

/** This form with the saved [snapshot]'s fields, when it belongs to the same income (or both are new). */
internal fun IncomeFormState.restoredFrom(snapshot: IncomeFormSnapshot?): IncomeFormState =
    if (snapshot == null || snapshot.incomeId != incomeId) {
        this
    } else {
        copy(
            source = snapshot.source,
            amountCents = snapshot.amountCents,
            date = Instant.ofEpochMilli(snapshot.dateEpochMillis),
            note = snapshot.note,
        )
    }
