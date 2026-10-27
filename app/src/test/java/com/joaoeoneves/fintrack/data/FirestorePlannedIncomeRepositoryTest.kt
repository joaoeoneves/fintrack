package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * [FirestorePlannedIncomeRepository]: the private top-level `toPlannedIncomeOrNull` mapper (reached by
 * reflection with a real `DocumentSnapshot`, see [FirestoreBudgetRepositoryTest]), the shared
 * `toFirestoreMap()`, and the signed-out behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestorePlannedIncomeRepositoryTest {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var repository: FirestorePlannedIncomeRepository

    private companion object {
        private const val TAG = "FirestorePlannedIncomeRepo"
    }

    @Before
    fun setUp() {
        ShadowLog.clear()
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
        val auth = FirebaseAuth.getInstance(app)
        auth.signOut()
        repository = FirestorePlannedIncomeRepository(firestore, auth)
    }

    private fun toPlannedIncomeOrNull(snapshot: DocumentSnapshot): PlannedIncome? {
        val method =
            Class
                .forName("com.joaoeoneves.fintrack.data.FirestorePlannedIncomeRepositoryKt")
                .getDeclaredMethod("toPlannedIncomeOrNull", DocumentSnapshot::class.java)
        method.isAccessible = true
        return method.invoke(null, snapshot) as PlannedIncome?
    }

    private fun snapshot(
        id: String,
        fields: Map<String, com.google.firestore.v1.Value>,
    ) = TestDocumentSnapshots.found(firestore, id, fields, collection = "plannedIncome")

    private fun fields(
        label: String? = "Salary",
        amount: Long? = 200_000L,
        order: Long? = 2L,
    ) = buildMap {
        label?.let { put("label", TestDocumentSnapshots.stringValue(it)) }
        amount?.let { put("amountCents", TestDocumentSnapshots.longValue(it)) }
        order?.let { put("order", TestDocumentSnapshots.longValue(it)) }
    }

    @Test
    fun wellFormedDoc_parses_idComesFromDocumentId_withoutWarning() {
        val result = toPlannedIncomeOrNull(snapshot("abc", fields()))

        assertEquals(PlannedIncome("abc", "Salary", 200_000L, 2), result)
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty())
    }

    @Test
    fun missingLabel_isDropped_andLogged() {
        assertNull(toPlannedIncomeOrNull(snapshot("a", fields(label = null))))
        assertTrue(ShadowLog.getLogsForTag(TAG).any { it.msg.contains("'label'") })
    }

    @Test
    fun missingAmount_isDropped_andLogged() {
        assertNull(toPlannedIncomeOrNull(snapshot("a", fields(amount = null))))
        assertTrue(ShadowLog.getLogsForTag(TAG).any { it.msg.contains("'amountCents'") })
    }

    @Test
    fun missingOrder_isDropped_andLogged() {
        assertNull(toPlannedIncomeOrNull(snapshot("a", fields(order = null))))
        assertTrue(ShadowLog.getLogsForTag(TAG).any { it.msg.contains("'order'") })
    }

    @Test
    fun toFirestoreMap_hasExactlyTheRuleFields_withoutId() {
        val map = PlannedIncome("x", "Meal allowance", 20_000L, 1).toFirestoreMapForTest()

        assertEquals(setOf("label", "amountCents", "order", "updatedAt"), map.keys)
        assertEquals("Meal allowance", map["label"])
        assertEquals(20_000L, map["amountCents"])
        assertEquals(1, map["order"])
        assertEquals(FieldValue.serverTimestamp(), map["updatedAt"])
    }

    private fun PlannedIncome.toFirestoreMapForTest(): Map<String, Any?> = toFirestoreMap()

    @Test
    fun signedOut_observeEmitsEmptyList() =
        runTest {
            assertEquals(emptyList<PlannedIncome>(), repository.observePlannedIncome().first())
        }

    @Test
    fun signedOut_saveFails_withNotSignedIn() =
        runTest {
            val result = repository.savePlannedIncome(PlannedIncome("", "Salary", 100L, 0))

            assertEquals("Not signed in", result.exceptionOrNull()?.message)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

    @Test
    fun signedOut_deleteFails_withNotSignedIn() =
        runTest {
            val result = repository.deletePlannedIncome("x")

            assertEquals("Not signed in", result.exceptionOrNull()?.message)
        }
}
