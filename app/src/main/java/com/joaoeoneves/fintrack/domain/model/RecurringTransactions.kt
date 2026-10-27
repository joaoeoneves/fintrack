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
): String = "rec_${itemId}_${ID_DATE_FORMAT.format(date)}"

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
