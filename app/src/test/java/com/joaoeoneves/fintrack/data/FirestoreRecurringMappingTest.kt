package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.google.firestore.v1.Value
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import com.joaoeoneves.fintrack.testutil.day
import com.joaoeoneves.fintrack.testutil.recurringItem
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

/** `DocumentSnapshot.toRecurringOrNull()` (real snapshots, see [TestDocumentSnapshots]) and `toFirestoreMap()`. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreRecurringMappingTest {
    private lateinit var firestore: FirebaseFirestore

    private companion object {
        private const val TAG = "FirestoreRecurringRepo"
    }

    @Before
    fun setUp() {
        ShadowLog.clear()
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
    }

    private fun str(value: String) = TestDocumentSnapshots.stringValue(value)

    private fun fields(vararg overrides: Pair<String, Value?>): Map<String, Value> {
        val base =
            mutableMapOf(
                "type" to str("EXPENSE"),
                "name" to str("Rent"),
                "amountCents" to TestDocumentSnapshots.longValue(50_000L),
                "category" to str("FOOD"),
                "frequency" to str("MONTHLY"),
                "dayOfMonth" to TestDocumentSnapshots.longValue(15L),
                "startDate" to str("2026-01-01"),
                "active" to TestDocumentSnapshots.booleanValue(true),
            )
        overrides.forEach { (key, value) -> if (value == null) base.remove(key) else base[key] = value }
        return base
    }

    private fun map(vararg o: Pair<String, Value?>) = snapshot(fields(*o)).toRecurringOrNull()

    private fun snapshot(fields: Map<String, Value>): DocumentSnapshot =
        TestDocumentSnapshots.found(firestore, "doc1", fields, collection = "recurring")

    @Test
    fun wellFormedExpense_parses_withoutWarning() {
        val item = map("note" to str("n"), "endDate" to str("2027-01-01"), "handledThrough" to str("2026-02-15"))

        assertEquals(
            recurringItem(id = "doc1", note = "n", endDate = day(2027, 1, 1), handledThrough = day(2026, 2, 15)),
            item?.copy(categoryId = CategoryId("FOOD")),
        )
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty())
    }

    @Test
    fun yearlyIncome_withoutCategory_parses() {
        val item =
            map(
                "type" to str("INCOME"),
                "category" to null,
                "frequency" to str("YEARLY"),
                "monthOfYear" to TestDocumentSnapshots.longValue(2L),
                "dayOfMonth" to TestDocumentSnapshots.longValue(29L),
            )

        assertEquals(RecurringType.INCOME, item?.type)
        assertNull(item?.categoryId)
        assertEquals(RecurringFrequency.YEARLY, item?.frequency)
        assertEquals(2, item?.monthOfYear)
        assertEquals(29, item?.dayOfMonth)
    }

    @Test
    fun missingRequiredField_isDropped_andLogged() {
        listOf("type", "frequency", "name", "amountCents", "dayOfMonth", "startDate", "active").forEach { key ->
            ShadowLog.clear()

            assertNull(key, map(key to null))
            assertTrue(key, ShadowLog.getLogsForTag(TAG).any { it.msg.contains("'$key'") })
        }
    }

    @Test
    fun unknownEnum_isDropped() {
        assertNull(map("type" to str("TRANSFER")))
        assertNull(map("frequency" to str("WEEKLY")))
    }

    @Test
    fun unparsableStartDate_isDropped() {
        assertNull(map("startDate" to str("15/01/2026")))
        assertNull(map("startDate" to str("2026-02-30")))
    }

    @Test
    fun unparsableEndOrHandledDate_isDropped_notTreatedAsAbsent() {
        assertNull(map("endDate" to str("soon")))
        assertNull(map("handledThrough" to str("")))
    }

    @Test
    fun inconsistentSchedule_isDropped() {
        assertNull(map("dayOfMonth" to TestDocumentSnapshots.longValue(32L)))
        assertNull(map("frequency" to str("YEARLY")))
        assertNull(map("monthOfYear" to TestDocumentSnapshots.longValue(3L)))
    }

    @Test
    fun expenseWithoutCategory_isDropped() {
        assertNull(map("category" to null))
        assertNull(map("category" to str("")))
    }

    @Test
    fun toFirestoreMap_hasExactlyTheRuleFields_nullsForAbsentOptionals() {
        val map = recurringItem(id = "ignored", note = "n", handledThrough = day(2026, 2, 15)).toFirestoreMap()

        assertEquals(
            setOf(
                "type",
                "name",
                "amountCents",
                "category",
                "note",
                "frequency",
                "dayOfMonth",
                "monthOfYear",
                "startDate",
                "endDate",
                "handledThrough",
                "active",
                "autoPost",
                "updatedAt",
            ),
            map.keys,
        )
        assertEquals("EXPENSE", map["type"])
        assertEquals("FOOD", map["category"])
        assertEquals("2026-01-01", map["startDate"])
        assertEquals("2026-02-15", map["handledThrough"])
        assertNull(map["endDate"])
        assertNull(map["monthOfYear"])
        assertEquals(FieldValue.serverTimestamp(), map["updatedAt"])
    }

    @Test
    fun toFirestoreMap_income_hasNullCategory_yearlyHasMonth() {
        val map =
            recurringItem(type = RecurringType.INCOME, frequency = RecurringFrequency.YEARLY, monthOfYear = 11)
                .toFirestoreMap()

        assertNull(map["category"])
        assertEquals(11, map["monthOfYear"])
        assertEquals("YEARLY", map["frequency"])
    }
}
