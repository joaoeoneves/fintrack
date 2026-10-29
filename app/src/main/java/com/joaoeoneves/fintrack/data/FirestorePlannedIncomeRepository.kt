package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.joaoeoneves.fintrack.data.local.earmarkOrNull
import com.joaoeoneves.fintrack.data.local.parseYearMonthOrNull
import com.joaoeoneves.fintrack.data.local.typeName
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.MAX_DAY_OF_MONTH
import com.joaoeoneves.fintrack.domain.model.MIN_DAY_OF_MONTH
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import java.time.YearMonth
import javax.inject.Inject

private const val TAG = "FirestorePlannedIncomeRepo"

class FirestorePlannedIncomeRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : PlannedIncomeRepository {
        private fun collection(uid: String) = firestore.collection("users").document(uid).collection("plannedIncome")

        override fun observePlannedIncome(): Flow<List<PlannedIncome>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

            return callbackFlow {
                val registration =
                    collection(uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.toPlannedIncomeList())
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun savePlannedIncome(line: PlannedIncome): Result<String> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val col = collection(uid)
                val docRef = if (line.id.isBlank()) col.document() else col.document(line.id)
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when
                // the device is online. Awaiting would hang indefinitely while offline.
                docRef
                    .set(line.toFirestoreMap())
                    .addOnFailureListener { Log.w(TAG, "Save planned income write ${docRef.id} failed", it) }
                docRef.id
            }
        }

        override suspend fun deletePlannedIncome(id: String): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                val docRef = collection(uid).document(id)
                // Not awaited (see savePlannedIncome); deleting a missing document is a no-op.
                docRef.delete().addOnFailureListener { Log.w(TAG, "Delete planned income failed: $id", it) }
                Unit
            }
        }

        private fun <T> notSignedIn(): Result<T> = Result.failure(IllegalStateException("Not signed in"))
    }

/** Written whole (no merge); the id is the document id. Shared with the guest-data migration. */
internal fun PlannedIncome.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        "label" to label,
        "amountCents" to amountCents,
        "order" to order,
        "basis" to basis.name,
        "frequency" to frequency.name,
        "monthOfYear" to monthOfYear,
        "yearMonth" to yearMonth?.toString(),
        "skipMonths" to skipMonths.sorted(),
        "updatedAt" to FieldValue.serverTimestamp(),
    ) + earmarkFields() + managedKeyFields() + automationFields() + planTimingFields(startMonth, versions)

// Both fields or neither; absent for an unearmarked line (the rules reject a null value).
private fun PlannedIncome.earmarkFields(): Map<String, Any?> =
    earmark?.let { mapOf("earmarkType" to it.typeName, "earmarkId" to it.id) }.orEmpty()

// Absent for a user's own line (the rules reject a null value).
private fun PlannedIncome.managedKeyFields(): Map<String, Any?> {
    val key = managedKey ?: return emptyMap()
    return mapOf("managedKey" to key)
}

// Absent while off / last working day (the rules reject a null value).
private fun PlannedIncome.automationFields(): Map<String, Any?> =
    buildMap {
        if (autoRecord) put("autoRecord", true)
        payDay?.let { put("payDay", it) }
    }

private fun QuerySnapshot.toPlannedIncomeList(): List<PlannedIncome> =
    documents.mapNotNull { it.toPlannedIncomeOrNull() }.sortedWith(compareBy({ it.order }, { it.id }))

private fun DocumentSnapshot.toPlannedIncomeOrNull() = mapOrNull("plannedIncome") { parsePlannedIncome() }

private fun DocumentSnapshot.parsePlannedIncome(): PlannedIncome? {
    val label = getString("label")
    val amountCents = getLong("amountCents")
    val order = getLong("order")
    val schedule = parseSchedule()

    return when {
        label == null -> dropped("label")
        amountCents == null -> dropped("amountCents")
        order == null -> dropped("order")
        schedule == null -> dropped("basis/frequency/monthOfYear/yearMonth")
        else ->
            PlannedIncome(
                id = id,
                label = label,
                amountCents = amountCents,
                order = order.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                basis = schedule.basis,
                frequency = schedule.frequency,
                monthOfYear = schedule.monthOfYear,
                yearMonth = schedule.yearMonth,
                skipMonths = schedule.skipMonths,
                earmark = earmarkOrNull(getString("earmarkType"), getString("earmarkId")),
                managedKey = getString("managedKey"),
                autoRecord = getBoolean("autoRecord") ?: false,
                payDay = getLong("payDay")?.toInt()?.takeIf { it in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH },
                startMonth = startMonthOrNull(),
                versions = versionsOrEmpty(),
            )
    }
}

/** The optional v2 fields of a planned income document; absent fields take their defaults. */
private class ParsedSchedule(
    val basis: IncomeBasis,
    val frequency: IncomeFrequency,
    val monthOfYear: Int?,
    val yearMonth: YearMonth?,
    val skipMonths: Set<Int>,
)

/** `null` when a present field is unknown or the fields do not fit the frequency. */
private fun DocumentSnapshot.parseSchedule(): ParsedSchedule? {
    val basis = enumField("basis", IncomeBasis.FIXED)
    val frequency = enumField("frequency", IncomeFrequency.MONTHLY)
    val monthOfYear = getLong("monthOfYear")?.toInt()
    val yearMonth = getString("yearMonth")?.let(::parseYearMonthOrNull)
    val consistent =
        when (frequency) {
            IncomeFrequency.YEARLY -> monthOfYear != null
            IncomeFrequency.ONCE -> yearMonth != null
            else -> true
        }
    val skipMonths = (get("skipMonths") as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }.toSet()
    return if (basis == null || frequency == null || !consistent) {
        null
    } else {
        ParsedSchedule(basis, frequency, monthOfYear, yearMonth, skipMonths)
    }
}

/** The enum constant named by [field], [default] when the field is absent, `null` when the name is unknown. */
private inline fun <reified E : Enum<E>> DocumentSnapshot.enumField(
    field: String,
    default: E,
): E? =
    getString(field)?.let { name -> enumValues<E>().find { it.name == name } }
        ?: default.takeUnless { contains(field) }

private fun DocumentSnapshot.dropped(field: String): PlannedIncome? {
    Log.w(TAG, "Dropping malformed planned income doc $id: missing or invalid '$field'")
    return null
}
