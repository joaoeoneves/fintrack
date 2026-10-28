package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.domain.model.LegacyExtraNames
import com.joaoeoneves.fintrack.domain.model.LegacySalaryExtras
import com.joaoeoneves.fintrack.domain.model.MAX_MONTH_OF_YEAR
import com.joaoeoneves.fintrack.domain.model.MAX_SALARY_EXTRAS
import com.joaoeoneves.fintrack.domain.model.PPM_SCALE
import com.joaoeoneves.fintrack.domain.model.SALARY_EXTRA_ID_PATTERN
import com.joaoeoneves.fintrack.domain.model.SalaryExtra
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountScope
import com.joaoeoneves.fintrack.domain.model.SalaryExtraAmountType
import com.joaoeoneves.fintrack.domain.model.SalaryExtraDeductions
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.amountRange
import com.joaoeoneves.fintrack.domain.model.toExtras
import com.joaoeoneves.fintrack.domain.repository.LegacyExtraNamesProvider
import com.joaoeoneves.fintrack.domain.repository.SalaryProfileRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

private const val TAG = "FirestoreSalaryProfileRepo"

/** The salary profile, the single document `users/{uid}/plan/salaryProfile`. Writes are not awaited. */
class FirestoreSalaryProfileRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
        private val legacyNames: LegacyExtraNamesProvider,
    ) : SalaryProfileRepository {
        private fun docRef(uid: String) = salaryProfileDoc(firestore, uid)

        override fun observeSalaryProfile(): Flow<SalaryProfile?> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(null)

            return callbackFlow {
                val registration =
                    docRef(uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.takeIf { it.exists() }?.toSalaryProfileOrNull(legacyNames.names()))
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun saveSalaryProfile(profile: SalaryProfile): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when online.
                docRef(uid)
                    .set(profile.toFirestoreMap())
                    .addOnFailureListener { Log.w(TAG, "Save salary profile failed", it) }
                Unit
            }
        }

        override suspend fun deleteSalaryProfile(): Result<Unit> {
            val uid = firebaseAuth.currentUser?.uid ?: return notSignedIn()
            return runCatchingCancellable {
                docRef(uid).delete().addOnFailureListener { Log.w(TAG, "Delete salary profile failed", it) }
                Unit
            }
        }

        private fun notSignedIn(): Result<Unit> = Result.failure(IllegalStateException("Not signed in"))
    }

/** The singleton document of [uid]'s salary profile. Shared with the guest-data migration. */
internal fun salaryProfileDoc(
    firestore: FirebaseFirestore,
    uid: String,
): DocumentReference =
    firestore
        .collection("users")
        .document(uid)
        .collection("plan")
        .document("salaryProfile")

/**
 * Written whole (no merge), so the legacy flex / profit-share / bonus fields drop out; optional fields are absent
 * when unset (the rules reject a null value) and `extras` is always present, even empty, once saved.
 */
internal fun SalaryProfile.toFirestoreMap(): Map<String, Any?> =
    buildMap {
        put("inputMode", inputMode.name)
        put("subsidySpreadPpm", subsidySpreadPpm)
        put("irsBasePpm", irsBasePpm)
        put("ssPpm", ssPpm)
        put("irsJovemExemptPpm", irsJovemExemptPpm)
        put("mealSkipMonths", mealSkipMonths.sorted())
        put("holidaySubsidyMonth", holidaySubsidyMonth)
        put("christmasSubsidyMonth", christmasSubsidyMonth)
        put("extras", extras.map { it.toFirestoreMap() })
        putIfSet("baseGrossCents", baseGrossCents)
        putIfSet("irsSubsidiesPpm", irsSubsidiesPpm)
        putIfSet("netMonthlyCents", netMonthlyCents)
        putIfSet("netHolidayLumpCents", netHolidayLumpCents)
        putIfSet("netChristmasLumpCents", netChristmasLumpCents)
        putIfSet("mealRatePerDayCents", mealRatePerDayCents)
        put("updatedAt", FieldValue.serverTimestamp())
    }

private fun SalaryExtra.toFirestoreMap(): Map<String, Any> =
    mapOf(
        "id" to id,
        "name" to name,
        "amountType" to amountType.name,
        "amount" to amount,
        "months" to months.sorted(),
        "deductions" to deductions.name,
        "feePpm" to feePpm,
    ).let { map ->
        if (amountScope == SalaryExtraAmountScope.PER_PAYMENT) map + ("amountScope" to amountScope.name) else map
    }

private fun MutableMap<String, Any?>.putIfSet(
    key: String,
    value: Any?,
) {
    if (value != null) put(key, value)
}

private fun DocumentSnapshot.toSalaryProfileOrNull(legacyNames: LegacyExtraNames) =
    mapOrNull("salaryProfile") { parseSalaryProfile(legacyNames) }

private fun DocumentSnapshot.parseSalaryProfile(legacyNames: LegacyExtraNames): SalaryProfile? {
    val defaults = SalaryProfile()
    val mode = getString("inputMode")?.let { name -> SalaryInputMode.entries.find { it.name == name } }
    if (mode == null) {
        Log.w(TAG, "Dropping malformed salary profile: missing or invalid 'inputMode'")
        return null
    }

    fun int(field: String) = getLong(field)?.toInt()
    return SalaryProfile(
        inputMode = mode,
        baseGrossCents = getLong("baseGrossCents"),
        subsidySpreadPpm = int("subsidySpreadPpm") ?: defaults.subsidySpreadPpm,
        irsBasePpm = int("irsBasePpm") ?: defaults.irsBasePpm,
        irsSubsidiesPpm = int("irsSubsidiesPpm"),
        ssPpm = int("ssPpm") ?: defaults.ssPpm,
        irsJovemExemptPpm = int("irsJovemExemptPpm") ?: defaults.irsJovemExemptPpm,
        netMonthlyCents = getLong("netMonthlyCents"),
        netHolidayLumpCents = getLong("netHolidayLumpCents"),
        netChristmasLumpCents = getLong("netChristmasLumpCents"),
        mealRatePerDayCents = getLong("mealRatePerDayCents"),
        mealSkipMonths =
            (get("mealSkipMonths") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet()
                ?: defaults.mealSkipMonths,
        holidaySubsidyMonth = int("holidaySubsidyMonth") ?: defaults.holidaySubsidyMonth,
        christmasSubsidyMonth = int("christmasSubsidyMonth") ?: defaults.christmasSubsidyMonth,
        extras = parseExtras(mode, legacyNames),
    )
}

/** The `extras` list (malformed ones dropped, at most [MAX_SALARY_EXTRAS]) or, absent, the migrated legacy fields. */
private fun DocumentSnapshot.parseExtras(
    mode: SalaryInputMode,
    legacyNames: LegacyExtraNames,
): List<SalaryExtra> {
    val raw = get("extras") as? List<*>
    if (raw != null) {
        return raw
            .mapNotNull { entry ->
                (entry as? Map<*, *>)?.toSalaryExtraOrNull().also {
                    if (it == null) Log.w(TAG, "Dropping malformed extra")
                }
            }.distinctBy { it.id }
            .take(MAX_SALARY_EXTRAS)
    }

    fun int(field: String) = getLong(field)?.toInt()
    return LegacySalaryExtras(
        flexPpm = int("flexPpm"),
        flexAnnualCents = getLong("flexAnnualCents"),
        flexFirstMonth = int("flexFirstMonth") ?: 1,
        flexWithdrawalFeePpm = int("flexWithdrawalFeePpm") ?: 0,
        profitShareMultiplierPpm = int("profitShareMultiplierPpm"),
        profitShareMonth = int("profitShareMonth"),
        profitShareMonths =
            (get("profitShareMonths") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet().orEmpty(),
        bonusCents = getLong("bonusCents"),
        bonusMonth = int("bonusMonth"),
    ).toExtras(mode, legacyNames)
}

@Suppress("ComplexCondition") // flat null checks of the independently parsed fields
private fun Map<*, *>.toSalaryExtraOrNull(): SalaryExtra? {
    val id = (this["id"] as? String)?.takeIf { SALARY_EXTRA_ID_PATTERN.matches(it) }
    val name = (this["name"] as? String)?.takeIf { it.isNotBlank() }
    val type = SalaryExtraAmountType.entries.find { it.name == this["amountType"] }
    val amount = (this["amount"] as? Number)?.toLong()?.takeIf { type != null && it in type.amountRange }
    val months =
        (this["months"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt()?.takeIf { m -> m in 1..MAX_MONTH_OF_YEAR } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
    val deductions =
        this["deductions"]?.let { value -> SalaryExtraDeductions.entries.find { it.name == value } }
            ?: SalaryExtraDeductions.NONE.takeIf { this["deductions"] == null }
    val scope =
        this["amountScope"]?.let { value -> SalaryExtraAmountScope.entries.find { it.name == value } }
            ?: SalaryExtraAmountScope.TOTAL.takeIf { this["amountScope"] == null }
    val fee = ((this["feePpm"] as? Number)?.toInt() ?: 0).coerceIn(0, PPM_SCALE)
    val complete = id != null && name != null && type != null && amount != null && months != null
    return if (complete && deductions != null && scope != null) {
        SalaryExtra(id, name, type, amount, months, deductions, fee, scope)
    } else {
        null
    }
}
