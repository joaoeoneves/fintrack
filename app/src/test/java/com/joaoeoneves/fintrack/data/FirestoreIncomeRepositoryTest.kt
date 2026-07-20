package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
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
import java.time.Instant

/**
 * Unit tests for [FirestoreIncomeRepository]'s private `toIncomeOrNull()` conversion. See
 * [FirestoreExpenseRepositoryTest] for the full rationale behind this approach (no mocking library
 * available; a real [DocumentSnapshot] is built via [TestDocumentSnapshots]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreIncomeRepositoryTest {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var repository: FirestoreIncomeRepository

    private companion object {
        private const val TAG = "FirestoreIncomeRepo"
    }

    @Before
    fun setUp() {
        ShadowLog.clear()
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
        repository = FirestoreIncomeRepository(firestore, FirebaseAuth.getInstance(app))
    }

    private fun toIncomeOrNull(snapshot: DocumentSnapshot): Income? {
        val method = FirestoreIncomeRepository::class.java.getDeclaredMethod("toIncomeOrNull", DocumentSnapshot::class.java)
        method.isAccessible = true
        return method.invoke(repository, snapshot) as Income?
    }

    private fun validFields(): Map<String, com.google.firestore.v1.Value> =
        mapOf(
            "source" to TestDocumentSnapshots.stringValue("Salary"),
            "amountCents" to TestDocumentSnapshots.longValue(300_000L),
            "date" to TestDocumentSnapshots.timestampValue(Timestamp(Instant.parse("2024-01-01T00:00:00Z"))),
            "note" to TestDocumentSnapshots.stringValue("January"),
        )

    @Test
    fun wellFormedDoc_parsesToIncome_andIsNotDropped() {
        val snapshot = TestDocumentSnapshots.found(firestore, id = "good1", fields = validFields(), collection = "income")

        val result = toIncomeOrNull(snapshot)

        assertEquals(
            Income(
                id = "good1",
                source = "Salary",
                amountCents = 300_000L,
                date = Instant.parse("2024-01-01T00:00:00Z"),
                note = "January",
            ),
            result,
        )
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty())
    }

    @Test
    fun malformedDoc_missingSource_isDroppedAsNull_andLogsWarning() {
        val snapshot = TestDocumentSnapshots.found(firestore, id = "bad-source", fields = validFields() - "source", collection = "income")

        val result = toIncomeOrNull(snapshot)

        assertNull(result)
        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue("expected a warning log for missing 'source', got: $logs", logs.any { it.msg.contains("'source'") })
    }

    @Test
    fun malformedDoc_missingAmountCents_isDroppedAsNull_andLogsWarning() {
        val snapshot =
            TestDocumentSnapshots.found(firestore, id = "bad-amount", fields = validFields() - "amountCents", collection = "income")

        val result = toIncomeOrNull(snapshot)

        assertNull(result)
        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue("expected a warning log for missing 'amountCents', got: $logs", logs.any { it.msg.contains("'amountCents'") })
    }

    @Test
    fun malformedDoc_missingDate_isDroppedAsNull_andLogsWarning() {
        val snapshot = TestDocumentSnapshots.found(firestore, id = "bad-date", fields = validFields() - "date", collection = "income")

        val result = toIncomeOrNull(snapshot)

        assertNull(result)
        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue("expected a warning log for missing 'date', got: $logs", logs.any { it.msg.contains("'date'") })
    }
}
