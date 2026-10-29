package com.joaoeoneves.fintrack.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.YearMonth
import javax.inject.Inject

class FirestoreBudgetRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
    ) : BudgetRepository {
        private fun budgetsCollection(uid: String) = firestore.collection("users").document(uid).collection("budgets")

        companion object {
            private const val TAG = "FirestoreBudgetRepo"
        }

        override fun observeBudgets(): Flow<List<Budget>> {
            val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

            return callbackFlow {
                val registration =
                    budgetsCollection(uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            trySend(snapshot.toBudgets())
                        }
                    }
                awaitClose { registration.remove() }
            }
        }

        override suspend fun setBudget(
            categoryId: CategoryId,
            limitCents: Long,
            skipMonths: Set<Int>,
            effectiveFrom: YearMonth,
        ): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val existing = observeBudgets().first().find { it.categoryId == categoryId }
                val saved = existing.savedAs(categoryId, limitCents, skipMonths, effectiveFrom)
                val data =
                    mapOf(
                        "limitCents" to saved.limitCents,
                        "skipMonths" to skipMonths.sorted(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ) + planTimingFields(saved.startMonth, saved.versions)
                val docRef = budgetsCollection(uid).document(categoryId.value)
                // Not awaited: the write lands in Firestore's local cache immediately and syncs when
                // the device is online. Awaiting would hang indefinitely while offline.
                docRef
                    .set(data, SetOptions.merge())
                    .addOnFailureListener { Log.w(TAG, "Set budget write ${docRef.id} failed", it) }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: Firestore Task failures aren't narrowly typed (network, permission,
                // and server errors all surface as generic/undocumented exception subtypes here);
                // convert any failure into a Result for the caller.
                Result.failure(e)
            }
        }

        override suspend fun clearBudget(categoryId: CategoryId): Result<Unit> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return try {
                val docRef = budgetsCollection(uid).document(categoryId.value)
                // Not awaited (see setBudget above); deleting a missing document is a no-op.
                docRef.delete().addOnFailureListener { Log.w(TAG, "Clear budget write ${docRef.id} failed", it) }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: see setBudget above.
                Result.failure(e)
            }
        }

        override suspend fun moveBudget(
            source: CategoryId,
            target: CategoryId,
        ): Result<Boolean> {
            val uid =
                firebaseAuth.currentUser?.uid
                    ?: return Result.failure(IllegalStateException("Not signed in"))
            return runCatchingCancellable {
                val sourceRef = budgetsCollection(uid).document(source.value)
                val targetRef = budgetsCollection(uid).document(target.value)
                val sourceDoc = sourceRef.get(Source.SERVER).await()
                if (sourceDoc.exists()) {
                    val batch = firestore.batch()
                    // Merge rule: an existing target budget wins; the source's is dropped.
                    if (!targetRef.get(Source.SERVER).await().exists()) {
                        val limit = sourceDoc.getLong("limitCents")
                        requireNotNull(limit) { "Budget ${source.value} has no limit" }
                        val skip = sourceDoc.skipMonths().sorted()
                        batch.set(
                            targetRef,
                            mapOf(
                                "limitCents" to limit,
                                "skipMonths" to skip,
                                "updatedAt" to FieldValue.serverTimestamp(),
                            ) + planTimingFields(sourceDoc.startMonthOrNull(), sourceDoc.versionsOrEmpty()),
                        )
                    }
                    batch.delete(sourceRef)
                    batch.commit().await()
                }
                sourceDoc.exists()
            }
        }

        private fun QuerySnapshot.toBudgets(): List<Budget> = documents.mapNotNull { it.toBudgetOrNull() }

        // The doc id is the category id. Any id is kept (even one matching no known category), so a
        // budget left behind by a deleted category still shows up and can be cleared.
        private fun DocumentSnapshot.toBudgetOrNull(): Budget? = mapOrNull("budget") { parseBudget() }

        private fun DocumentSnapshot.parseBudget(): Budget? {
            val limitCents = getLong("limitCents")
            if (limitCents == null) {
                Log.w(TAG, "Dropping malformed budget doc $id: missing or invalid 'limitCents'")
            }
            return limitCents?.let {
                Budget(
                    categoryId = CategoryId(id),
                    limitCents = it,
                    skipMonths = skipMonths(),
                    startMonth = startMonthOrNull(),
                    versions = versionsOrEmpty(),
                )
            }
        }

        // Optional list of month numbers; anything that isn't a number is ignored.
        private fun DocumentSnapshot.skipMonths(): Set<Int> =
            (get("skipMonths") as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }.toSet()
    }
