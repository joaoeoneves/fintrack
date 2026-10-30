package com.joaoeoneves.fintrack.ui.recurring

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.resolve
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** The fields of the recurring form that survive process death. */
@Serializable
data class RecurringFormSnapshot(
    val recurringId: String?,
    val startDate: String,
    val type: String,
    val name: String,
    val amountCents: Long,
    val categoryId: String?,
    val note: String,
    val frequency: String,
    val dayText: String,
    val monthOfYear: Int?,
    val endDate: String?,
    val paused: Boolean,
)

internal fun RecurringForm.toSnapshot(recurringId: String?) =
    RecurringFormSnapshot(
        recurringId = recurringId,
        startDate = startDate.toString(),
        type = type.name,
        name = name,
        amountCents = amountCents,
        categoryId = category?.id?.value,
        note = note,
        frequency = frequency.name,
        dayText = dayText,
        monthOfYear = monthOfYear,
        endDate = endDate?.toString(),
        paused = paused,
    )

/** The saved form, or `null` when the snapshot is for another item or no longer parses. */
internal fun RecurringFormSnapshot.toForm(
    recurringId: String?,
    catalog: List<Category>,
): RecurringForm? =
    if (this.recurringId != recurringId) {
        null
    } else {
        runCatching {
            RecurringForm(
                startDate = LocalDate.parse(startDate),
                type = RecurringType.valueOf(type),
                name = name,
                amountCents = amountCents,
                category = categoryId?.let { catalog.resolve(CategoryId(it)) },
                note = note,
                frequency = RecurringFrequency.valueOf(frequency),
                dayText = dayText,
                monthOfYear = monthOfYear,
                endDate = endDate?.let(LocalDate::parse),
                        paused = paused,
            )
        }.getOrNull()
    }
