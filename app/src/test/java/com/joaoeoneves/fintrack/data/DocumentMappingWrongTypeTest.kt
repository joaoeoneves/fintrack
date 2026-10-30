package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.google.firestore.v1.Value
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import org.junit.Assert.assertNotNull
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
 * A field of the wrong Firestore type makes the typed getters throw (RuntimeException); every mapper
 * must absorb that (via `mapOrNull`), return null and log a warning instead of failing the listener.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DocumentMappingWrongTypeTest {
    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setUp() {
        ShadowLog.clear()
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
    }

    private fun str(v: String) = TestDocumentSnapshots.stringValue(v)

    private fun lng(v: Long) = TestDocumentSnapshots.longValue(v)

    private fun doc(
        collection: String,
        fields: Map<String, Value>,
    ): DocumentSnapshot = TestDocumentSnapshots.found(firestore, "doc1", fields, collection = collection)

    private fun date() = TestDocumentSnapshots.timestampValue(Timestamp(Instant.parse("2024-01-15T00:00:00Z")))

    private fun callTop(
        file: String,
        name: String,
        snapshot: DocumentSnapshot,
    ): Any? {
        val m =
            Class
                .forName("com.joaoeoneves.fintrack.data.$file")
                .getDeclaredMethod(name, DocumentSnapshot::class.java)
        m.isAccessible = true
        return m.invoke(null, snapshot)
    }

    private fun assertDroppedAndLogged() {
        assertTrue(
            "expected a Log.w naming the doc, got ${ShadowLog.getLogs()}",
            ShadowLog.getLogsForTag("DocumentMapping").any { it.msg.contains("doc1") },
        )
    }

    private fun expenseFields() =
        mapOf("name" to str("Coffee"), "amountCents" to lng(500L), "category" to str("SHOPPING"), "date" to date())

    private fun incomeFields() = mapOf("source" to str("Salary"), "amountCents" to lng(900L), "date" to date())

    @Test
    fun expense_numericName_isDropped_andLogged() {
        val bad = expenseFields() + ("name" to lng(5L))

        assertNull(callTop("FirestoreExpenseRepositoryKt", "toExpenseOrNull", doc("expenses", bad)))
        assertDroppedAndLogged()
    }

    @Test
    fun expense_stringAmountCents_isDropped_andValidDocStillMaps() {
        val bad = expenseFields() + ("amountCents" to str("500"))

        assertNull(callTop("FirestoreExpenseRepositoryKt", "toExpenseOrNull", doc("expenses", bad)))
        assertDroppedAndLogged()
        assertNotNull(callTop("FirestoreExpenseRepositoryKt", "toExpenseOrNull", doc("expenses", expenseFields())))
    }

    @Test
    fun income_numericSource_isDropped_andValidDocStillMaps() {
        val bad = incomeFields() + ("source" to lng(1L))

        assertNull(callTop("FirestoreIncomeRepositoryKt", "toIncomeOrNull", doc("income", bad)))
        assertDroppedAndLogged()
        assertNotNull(callTop("FirestoreIncomeRepositoryKt", "toIncomeOrNull", doc("income", incomeFields())))
    }

    @Test
    fun income_stringAmountCents_isDropped_andLogged() {
        val bad = incomeFields() + ("amountCents" to str("900"))

        assertNull(callTop("FirestoreIncomeRepositoryKt", "toIncomeOrNull", doc("income", bad)))
        assertDroppedAndLogged()
    }

    @Test
    fun plannedIncome_numericLabel_isDropped_andLogged() {
        val bad = mapOf("label" to lng(1L), "amountCents" to lng(100L), "order" to lng(1L))

        assertNull(callTop("FirestorePlannedIncomeRepositoryKt", "toPlannedIncomeOrNull", doc("plannedIncome", bad)))
        assertDroppedAndLogged()
    }

    @Test
    fun plannedIncome_stringOrder_isDropped_andValidDocStillMaps() {
        val ok = mapOf("label" to str("Pay"), "amountCents" to lng(100L), "order" to lng(1L))
        val bad = ok + ("order" to str("1"))

        assertNull(callTop("FirestorePlannedIncomeRepositoryKt", "toPlannedIncomeOrNull", doc("plannedIncome", bad)))
        assertDroppedAndLogged()
        assertNotNull(callTop("FirestorePlannedIncomeRepositoryKt", "toPlannedIncomeOrNull", doc("plannedIncome", ok)))
    }

    @Test
    fun recurring_stringDayOfMonth_isDropped_andLogged() {
        val bad =
            mapOf(
                "type" to str("EXPENSE"),
                "name" to str("Rent"),
                "amountCents" to lng(50_000L),
                "category" to str("FOOD"),
                "frequency" to str("MONTHLY"),
                "dayOfMonth" to str("15"),
                "startDate" to str("2026-01-01"),
                "active" to TestDocumentSnapshots.booleanValue(true),
            )

        assertNull(doc("recurring", bad).toRecurringOrNull())
        assertDroppedAndLogged()
    }

    @Test
    fun category_numericName_isDropped_andLogged() {
        val bad = mapOf("name" to lng(7L), "kind" to str("EXPENSE"))

        assertNull(doc("categories", bad).toCategoryOrNull())
        assertDroppedAndLogged()
    }

    @Test
    fun category_validDoc_stillMaps_withoutWarning() {
        val ok = mapOf("name" to str("Gym"), "kind" to str("EXPENSE"))

        assertNotNull(doc("categories", ok).toCategoryOrNull())
        assertTrue(ShadowLog.getLogsForTag("DocumentMapping").isEmpty())
    }
}
