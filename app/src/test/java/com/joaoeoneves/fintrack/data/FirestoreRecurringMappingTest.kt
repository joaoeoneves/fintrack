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
    fun income_isSkipped() {
        val item =
            map(
                "type" to str("INCOME"),
                "category" to null,
                "frequency" to str("YEARLY"),
                "monthOfYear" to TestDocumentSnapshots.longValue(2L),
                "dayOfMonth" to TestDocumentSnapshots.longValue(29L),
            )

        assertNull(item)
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
                "skipped",
                "pausedAt",
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

    @Test
    fun skippedAndPausedAt_roundTripThroughTheMap() {
        val item =
            recurringItem(
                id = "doc1",
                skipped = setOf(day(2026, 3, 15), day(2026, 2, 15)),
                pausedAt = day(2026, 3, 1),
            )
        val map = item.toFirestoreMap()

        assertEquals(listOf("2026-02-15", "2026-03-15"), map["skipped"])
        assertEquals("2026-03-01", map["pausedAt"])

        val parsed =
            map(
                "skipped" to TestDocumentSnapshots.arrayValue("2026-02-15", "2026-03-15"),
                "pausedAt" to str("2026-03-01"),
            )

        assertEquals(item.skipped, parsed?.skipped)
        assertEquals(item.pausedAt, parsed?.pausedAt)
    }

    @Test
    fun missingSkippedAndPausedAt_giveEmptyAndNull() {
        val item = map()

        assertTrue(item?.skipped?.isEmpty() == true)
        assertNull(item?.pausedAt)
        assertNull(recurringItem().toFirestoreMap()["pausedAt"])
        assertEquals(emptyList<String>(), recurringItem().toFirestoreMap()["skipped"])
    }

    @Test
    fun badSkippedEntries_areDropped_butTheItemIsKept() {
        val item =
            map(
                "skipped" to
                    TestDocumentSnapshots.arrayValue(
                        "2026-02-15",
                        5L,
                        "not-a-date",
                        "2026-02-30",
                        "2026-03-15",
                    ),
            )

        assertEquals(setOf(day(2026, 2, 15), day(2026, 3, 15)), item?.skipped)
    }

    @Test
    fun aSkippedFieldThatIsNotAnArray_isIgnored() {
        val item = map("skipped" to str("2026-02-15"))

        assertTrue(item != null)
        assertTrue(item?.skipped?.isEmpty() == true)
    }

    @Test
    fun unparsablePausedAt_becomesNull_andTheItemIsKept() {
        val item = map("pausedAt" to str("yesterday"))

        assertTrue(item != null)
        assertNull(item?.pausedAt)
    }

    @Test
    fun toFirestoreMap_alwaysWritesTheAdoptionMarker() {
        assertEquals(true, recurringItem().toFirestoreMap()["autoPost"])
        assertEquals(true, recurringItem(active = false).toFirestoreMap()["autoPost"])
    }

    @Test
    fun isLegacyManual_isTrueForAFalseOrAbsentMarker_falseWhenTrue() {
        assertTrue(snapshot(fields()).isLegacyManual())
        assertTrue(snapshot(fields("autoPost" to TestDocumentSnapshots.booleanValue(false))).isLegacyManual())
        assertTrue(!snapshot(fields("autoPost" to TestDocumentSnapshots.booleanValue(true))).isLegacyManual())
    }

    @Test
    fun isLegacyManual_aNonBooleanMarker_countsAsLegacy() {
        assertTrue(snapshot(fields("autoPost" to str("true"))).isLegacyManual())
    }

    @Test
    fun mapper_ignoresTheMarker_bothValuesMapToTheSameItem() {
        val off = map("autoPost" to TestDocumentSnapshots.booleanValue(false))
        val on = map("autoPost" to TestDocumentSnapshots.booleanValue(true))

        assertEquals(off, on)
        assertEquals(map(), on)
    }

    @Test
    fun legacyAdoptionUpdate_movesTheCursorToTheLastOccurrenceBeforeToday_andSetsTheMarker() {
        val item = recurringItem(id = "doc1", handledThrough = day(2026, 1, 15))

        val update = legacyAdoptionUpdate(item, day(2026, 4, 20))

        assertEquals(setOf("handledThrough", "autoPost", "updatedAt"), update.keys)
        assertEquals("2026-04-15", update["handledThrough"])
        assertEquals(true, update["autoPost"])
        assertEquals(FieldValue.serverTimestamp(), update["updatedAt"])
    }

    @Test
    fun legacyAdoptionUpdate_todaysOccurrenceStaysPending() {
        val update = legacyAdoptionUpdate(recurringItem(handledThrough = day(2026, 1, 15)), day(2026, 4, 15))

        assertEquals("2026-03-15", update["handledThrough"])
    }

    @Test
    fun legacyAdoptionUpdate_neverMovesTheCursorBackwards() {
        val update = legacyAdoptionUpdate(recurringItem(handledThrough = day(2026, 6, 15)), day(2026, 4, 20))

        assertEquals("2026-06-15", update["handledThrough"])
    }

    @Test
    fun legacyAdoptionUpdate_futureStart_clearsNothingAndKeepsNullCursor() {
        val update = legacyAdoptionUpdate(recurringItem(startDate = day(2026, 9, 1)), day(2026, 4, 20))

        assertNull(update["handledThrough"])
        assertEquals(true, update["autoPost"])
    }

    @Test
    fun legacyAdoptionUpdate_unmappableItem_onlySetsTheMarker() {
        val update = legacyAdoptionUpdate(null, day(2026, 4, 20))

        assertEquals(setOf("autoPost", "updatedAt"), update.keys)
        assertEquals(true, update["autoPost"])
    }
}
