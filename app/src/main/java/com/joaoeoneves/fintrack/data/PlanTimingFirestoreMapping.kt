package com.joaoeoneves.fintrack.data

import com.google.firebase.firestore.DocumentSnapshot
import com.joaoeoneves.fintrack.data.local.parseYearMonthOrNull
import com.joaoeoneves.fintrack.domain.model.AmountVersion
import com.joaoeoneves.fintrack.domain.model.MAX_AMOUNT_VERSIONS
import java.time.YearMonth

/** `startMonth` ("yyyy-MM") and `versions` fields; each is omitted when null / empty. */
internal fun planTimingFields(
    startMonth: YearMonth?,
    versions: List<AmountVersion>,
): Map<String, Any> =
    buildMap {
        startMonth?.let { put("startMonth", it.toString()) }
        if (versions.isNotEmpty()) put("versions", versions.map { it.toFirestoreMap() })
    }

private fun AmountVersion.toFirestoreMap() = mapOf("from" to from.toString(), "amountCents" to amountCents)

/** Lenient: an unparsable `startMonth` is null. */
internal fun DocumentSnapshot.startMonthOrNull(): YearMonth? = getString("startMonth")?.let(::parseYearMonthOrNull)

/** Lenient: malformed entries (not a map, bad month, non-numeric or negative amount) are dropped. */
internal fun DocumentSnapshot.versionsOrEmpty(): List<AmountVersion> =
    (get("versions") as? List<*>)
        .orEmpty()
        .mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val from = (map["from"] as? String)?.let(::parseYearMonthOrNull) ?: return@mapNotNull null
            val amount = map["amountCents"]
            when {
                amount == null -> AmountVersion(from, null)
                amount is Number && amount.toLong() >= 0 -> AmountVersion(from, amount.toLong())
                else -> null
            }
        }.take(MAX_AMOUNT_VERSIONS)
