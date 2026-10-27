package com.joaoeoneves.fintrack.domain.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ID_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE

/**
 * Deterministic id of the transaction generated for [date] of item [itemId]: `rec_<itemId>_<yyyyMMdd>`.
 * It is the only link between a recurring item and its transactions.
 */
fun occurrenceTransactionId(
    itemId: String,
    date: LocalDate,
): String = "$OCCURRENCE_ID_PREFIX${itemId}_${ID_DATE_FORMAT.format(date)}"

private const val OCCURRENCE_ID_PREFIX = "rec_"

/** Whether [id] is the id of a transaction generated from a recurring item (see [occurrenceTransactionId]). */
fun isRecurringOccurrenceId(id: String): Boolean = id.startsWith(OCCURRENCE_ID_PREFIX)

/**
 * The ordinary transaction for [occurrence] of this item, dated at noon in [zone] so the local day
 * is stable across zones. An expense resolves its category in [catalog]; an income uses the item's
 * name as its source.
 */
fun RecurringItem.transactionFor(
    occurrence: LocalDate,
    zone: ZoneId,
    catalog: List<Category>,
): Transaction {
    val date = occurrence.atTime(LocalTime.NOON).atZone(zone).toInstant()
    val transactionId = occurrenceTransactionId(id, occurrence)
    return when (type) {
        RecurringType.EXPENSE ->
            Expense(
                id = transactionId,
                name = name,
                amountCents = amountCents,
                category = catalog.resolve(requireNotNull(categoryId) { "Expense item without category" }),
                date = date,
                note = note,
            )
        RecurringType.INCOME ->
            Income(id = transactionId, source = name, amountCents = amountCents, date = date, note = note)
    }
}
