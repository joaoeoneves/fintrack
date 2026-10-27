package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
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
                            trySend(snapshot.takeIf { it.exists() }?.toSalaryProfileOrNull())
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

/** Written whole (no merge); optional fields are absent when unset (the rules reject a null value). */
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
        put("flexFirstMonth", flexFirstMonth)
        put("flexWithdrawalFeePpm", flexWithdrawalFeePpm)
        putIfSet("baseGrossCents", baseGrossCents)
        putIfSet("irsSubsidiesPpm", irsSubsidiesPpm)
        putIfSet("netMonthlyCents", netMonthlyCents)
        putIfSet("netHolidayLumpCents", netHolidayLumpCents)
        putIfSet("netChristmasLumpCents", netChristmasLumpCents)
        putIfSet("mealRatePerDayCents", mealRatePerDayCents)
        putIfSet("flexPpm", flexPpm)
        putIfSet("flexAnnualCents", flexAnnualCents)
        putIfSet("profitShareMultiplierPpm", profitShareMultiplierPpm)
        putIfSet("profitShareMonth", profitShareMonth)
        putIfSet("bonusCents", bonusCents)
        putIfSet("bonusMonth", bonusMonth)
        put("updatedAt", FieldValue.serverTimestamp())
    }

private fun MutableMap<String, Any?>.putIfSet(
    key: String,
    value: Any?,
) {
    if (value != null) put(key, value)
}

private fun DocumentSnapshot.toSalaryProfileOrNull() = mapOrNull("salaryProfile") { parseSalaryProfile() }

private fun DocumentSnapshot.parseSalaryProfile(): SalaryProfile? {
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
        flexPpm = int("flexPpm"),
        flexAnnualCents = getLong("flexAnnualCents"),
        flexFirstMonth = int("flexFirstMonth") ?: defaults.flexFirstMonth,
        flexWithdrawalFeePpm = int("flexWithdrawalFeePpm") ?: defaults.flexWithdrawalFeePpm,
        profitShareMultiplierPpm = int("profitShareMultiplierPpm"),
        profitShareMonth = int("profitShareMonth"),
        bonusCents = getLong("bonusCents"),
        bonusMonth = int("bonusMonth"),
    )
}
