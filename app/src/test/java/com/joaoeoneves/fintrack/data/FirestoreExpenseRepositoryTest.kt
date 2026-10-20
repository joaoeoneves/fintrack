package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.joaoeoneves.fintrack.domain.model.Expense
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
 * Unit tests for [FirestoreExpenseRepository]'s private top-level `toExpenseOrNull()` conversion
 * (a file-private `DocumentSnapshot` extension, not a class member -- Kotlin compiles it onto a
 * synthetic `FirestoreExpenseRepositoryKt` facade class, invoked here as a static method), exercised
 * directly via reflection on a real [DocumentSnapshot] (built through [TestDocumentSnapshots], which
 * uses Firestore's own package-private `DocumentSnapshot.fromDocument` factory -- see that file's doc
 * comment). This project has no mocking library (see `FirebaseAuthRepositoryTest`), so this is the
 * only way to exercise the actual malformed-doc-drop logic end to end rather than re-implementing it.
 *
 * A real (but network-inert -- nothing here ever calls `.get()`/`.collection()`) [FirebaseFirestore]
 * is required only because `DocumentSnapshot`'s factory requires a non-null instance; see
 * [FirebaseTestApp].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreExpenseRepositoryTest {
    private lateinit var firestore: FirebaseFirestore

    private companion object {
        private const val TAG = "FirestoreExpenseRepo"
    }

    @Before
    fun setUp() {
        ShadowLog.clear()
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
    }

    private fun toExpenseOrNull(snapshot: DocumentSnapshot): Expense? {
        val method =
            Class
                .forName("com.joaoeoneves.fintrack.data.FirestoreExpenseRepositoryKt")
                .getDeclaredMethod("toExpenseOrNull", DocumentSnapshot::class.java)
        method.isAccessible = true
        return method.invoke(null, snapshot) as Expense?
    }

    private fun validFields(): Map<String, com.google.firestore.v1.Value> =
        mapOf(
            "name" to TestDocumentSnapshots.stringValue("Coffee"),
            "amountCents" to TestDocumentSnapshots.longValue(500L),
            "category" to TestDocumentSnapshots.stringValue("SHOPPING"),
            "date" to TestDocumentSnapshots.timestampValue(Timestamp(Instant.parse("2024-01-15T00:00:00Z"))),
            "note" to TestDocumentSnapshots.stringValue("with milk"),
        )

    // ---- sanity: a well-formed doc parses correctly (proves the test harness itself works) ----

    @Test
    fun wellFormedDoc_parsesToExpense_andIsNotDropped() {
        val snapshot = TestDocumentSnapshots.found(firestore, id = "good1", fields = validFields())

        val result = toExpenseOrNull(snapshot)

        assertEquals(
            Expense(
                id = "good1",
                name = "Coffee",
                amountCents = 500L,
                category = com.joaoeoneves.fintrack.domain.model.ExpenseCategory.SHOPPING,
                date = Instant.parse("2024-01-15T00:00:00Z"),
                note = "with milk",
            ),
            result,
        )
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty())
    }

    @Test
    fun wellFormedDoc_withNullNote_parsesCorrectly() {
        val snapshot = TestDocumentSnapshots.found(firestore, id = "good2", fields = validFields() - "note")

        val result = toExpenseOrNull(snapshot)

        assertEquals(null, result?.note)
        assertEquals("Coffee", result?.name)
    }

    // ---- malformed docs: each missing/invalid field drops the doc (returns null) and logs a warning ----

    @Test
    fun malformedDoc_missingName_isDroppedAsNull_andLogsWarning() {
        val snapshot = TestDocumentSnapshots.found(firestore, id = "bad-name", fields = validFields() - "name")

        val result = toExpenseOrNull(snapshot)

        assertNull(result)
        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue("expected a warning log for missing 'name', got: $logs", logs.any { it.msg.contains("'name'") })
    }

    @Test
    fun malformedDoc_missingAmountCents_isDroppedAsNull_andLogsWarning() {
        val snapshot = TestDocumentSnapshots.found(firestore, id = "bad-amount", fields = validFields() - "amountCents")

        val result = toExpenseOrNull(snapshot)

        assertNull(result)
        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue(
            "expected a warning log for missing 'amountCents', got: $logs",
            logs.any { it.msg.contains("'amountCents'") },
        )
    }

    @Test
    fun malformedDoc_missingCategory_isDroppedAsNull_andLogsWarning() {
        val snapshot = TestDocumentSnapshots.found(firestore, id = "bad-category", fields = validFields() - "category")

        val result = toExpenseOrNull(snapshot)

        assertNull(result)
        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue(
            "expected a warning log for missing 'category', got: $logs",
            logs.any { it.msg.contains("'category'") },
        )
    }

    @Test
    fun malformedDoc_invalidCategoryEnumValue_isDroppedAsNull_andLogsWarning() {
        val fields = validFields() + ("category" to TestDocumentSnapshots.stringValue("NOT_A_REAL_CATEGORY"))
        val snapshot = TestDocumentSnapshots.found(firestore, id = "bad-category-enum", fields = fields)

        val result = toExpenseOrNull(snapshot)

        assertNull(result)
        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue(
            "expected a warning log for invalid 'category', got: $logs",
            logs.any { it.msg.contains("'category'") },
        )
    }

    @Test
    fun foodCategory_parsesToFood_andIsNotDropped() {
        val fields = validFields() + ("category" to TestDocumentSnapshots.stringValue("FOOD"))
        val snapshot = TestDocumentSnapshots.found(firestore, id = "food1", fields = fields)

        val result = toExpenseOrNull(snapshot)

        assertEquals(com.joaoeoneves.fintrack.domain.model.ExpenseCategory.FOOD, result?.category)
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty())
    }

    @Test
    fun unknownCategoryName_groceries_isStillDroppedAsNull() {
        val fields = validFields() + ("category" to TestDocumentSnapshots.stringValue("GROCERIES"))
        val snapshot = TestDocumentSnapshots.found(firestore, id = "groceries", fields = fields)

        assertNull(toExpenseOrNull(snapshot))
        assertTrue(ShadowLog.getLogsForTag(TAG).any { it.msg.contains("'category'") })
    }

    @Test
    fun malformedDoc_missingDate_isDroppedAsNull_andLogsWarning() {
        val snapshot = TestDocumentSnapshots.found(firestore, id = "bad-date", fields = validFields() - "date")

        val result = toExpenseOrNull(snapshot)

        assertNull(result)
        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue("expected a warning log for missing 'date', got: $logs", logs.any { it.msg.contains("'date'") })
    }

    @Test
    fun malformedDoc_logMessage_includesDocumentId() {
        val snapshot = TestDocumentSnapshots.found(firestore, id = "doc-id-12345", fields = validFields() - "name")

        toExpenseOrNull(snapshot)

        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue("expected the doc id in the log message, got: $logs", logs.any { it.msg.contains("doc-id-12345") })
    }

    // ---- isRefund ----

    @Test
    fun isRefundTrue_parsesAsRefund() {
        val fields = validFields() + ("isRefund" to TestDocumentSnapshots.booleanValue(true))
        val snapshot = TestDocumentSnapshots.found(firestore, id = "refund1", fields = fields)

        val result = toExpenseOrNull(snapshot)

        assertEquals(true, result?.isRefund)
        assertEquals(500L, result?.amountCents)
        assertEquals(-500L, result?.signedAmountCents)
    }

    @Test
    fun isRefundFalse_parsesAsRegularExpense() {
        val fields = validFields() + ("isRefund" to TestDocumentSnapshots.booleanValue(false))
        val snapshot = TestDocumentSnapshots.found(firestore, id = "regular1", fields = fields)

        assertEquals(false, toExpenseOrNull(snapshot)?.isRefund)
    }

    @Test
    fun isRefundAbsent_legacyDoc_defaultsToFalse_andIsNotDropped() {
        val snapshot = TestDocumentSnapshots.found(firestore, id = "legacy1", fields = validFields())

        val result = toExpenseOrNull(snapshot)

        assertEquals(false, result?.isRefund)
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty())
    }

    @Test
    fun isRefundWrongType_defaultsToFalse_andIsNotDropped() {
        val fields = validFields() + ("isRefund" to TestDocumentSnapshots.stringValue("true"))
        val snapshot = TestDocumentSnapshots.found(firestore, id = "weird1", fields = fields)

        val result = toExpenseOrNull(snapshot)

        assertEquals(false, result?.isRefund)
        assertEquals("Coffee", result?.name)
    }

    @Test
    fun toFirestoreMap_alwaysContainsIsRefund_forBothRefundsAndRegularExpenses() {
        val base =
            Expense(
                id = "e1",
                name = "Coffee",
                amountCents = 500L,
                category = com.joaoeoneves.fintrack.domain.model.ExpenseCategory.SHOPPING,
                date = Instant.parse("2024-01-15T00:00:00Z"),
                note = null,
            )

        val regular = toFirestoreMap(base, includeCreatedAt = false)
        val refund = toFirestoreMap(base.copy(isRefund = true), includeCreatedAt = true)

        assertTrue(regular.containsKey("isRefund"))
        assertEquals(false, regular["isRefund"])
        assertEquals(true, refund["isRefund"])
        // amountCents is always stored as the positive magnitude; the flag carries the sign.
        assertEquals(500L, refund["amountCents"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun toFirestoreMap(
        expense: Expense,
        includeCreatedAt: Boolean,
    ): Map<String, Any?> {
        val method =
            Class
                .forName("com.joaoeoneves.fintrack.data.FirestoreExpenseRepositoryKt")
                .getDeclaredMethod("toFirestoreMap", Expense::class.java, Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(null, expense, includeCreatedAt) as Map<String, Any?>
    }
}
