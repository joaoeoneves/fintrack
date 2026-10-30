package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.google.firestore.v1.Value
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The 5.7 `refundOf` / `splitId` fields: written when set, deleted explicitly on update when cleared (updates merge,
 * so an absent key would leave a stale link), read back as null when absent or blank.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreExpenseLinksTest {
    private lateinit var firestore: FirebaseFirestore
    private val date = Instant.parse("2026-10-01T12:00:00Z")

    @Before
    fun setUp() {
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
    }

    private fun expense(
        refundOf: String? = null,
        splitId: String? = null,
    ) = Expense(
        "e1",
        "Rent",
        500,
        BuiltInCategories.FOOD,
        date,
        null,
        isRefund = refundOf != null,
        refundOf = refundOf,
        splitId = splitId,
    )

    private fun str(value: String) = TestDocumentSnapshots.stringValue(value)

    private fun parse(vararg extra: Pair<String, Value>): Expense {
        val snapshot =
            TestDocumentSnapshots.found(
                firestore,
                "x1",
                mapOf(
                    "name" to TestDocumentSnapshots.stringValue("Rent"),
                    "amountCents" to TestDocumentSnapshots.longValue(500),
                    "category" to TestDocumentSnapshots.stringValue("FOOD"),
                    "date" to TestDocumentSnapshots.timestampValue(Timestamp(date)),
                    *extra,
                ),
                collection = "expenses",
            )
        val method =
            Class
                .forName("com.joaoeoneves.fintrack.data.FirestoreExpenseRepositoryKt")
                .getDeclaredMethod("toExpenseOrNull", DocumentSnapshot::class.java)
        method.isAccessible = true
        return method.invoke(null, snapshot) as Expense
    }

    private fun isDelete(value: Any?) = value == FieldValue.delete()

    @Test
    fun create_writesTheKeysOnlyWhenSet() {
        val set = expense(refundOf = "orig", splitId = "grp").toFirestoreMap(includeCreatedAt = true)
        val none = expense().toFirestoreMap(includeCreatedAt = true)

        assertEquals("orig", set["refundOf"])
        assertEquals("grp", set["splitId"])
        assertFalse(none.containsKey("refundOf"))
        assertFalse(none.containsKey("splitId"))
    }

    @Test
    fun update_writesSetValues_andDeletesClearedOnes() {
        val set = expense(refundOf = "orig", splitId = "grp").toFirestoreMap(includeCreatedAt = false)
        val cleared = expense().toFirestoreMap(includeCreatedAt = false)

        assertEquals("orig", set["refundOf"])
        assertEquals("grp", set["splitId"])
        assertTrue(isDelete(cleared["refundOf"]))
        assertTrue(isDelete(cleared["splitId"]))
    }

    @Test
    fun update_clearingOnlyOneLink_deletesOnlyThatKey() {
        val map = expense(splitId = "grp").toFirestoreMap(includeCreatedAt = false)

        assertTrue(isDelete(map["refundOf"]))
        assertEquals("grp", map["splitId"])
    }

    @Test
    fun read_valuesAbsentAndBlank() {
        val full = parse("refundOf" to str("orig"), "splitId" to str("grp"))
        assertEquals("orig", full.refundOf)
        assertEquals("grp", full.splitId)

        val absent = parse()
        assertNull(absent.refundOf)
        assertNull(absent.splitId)

        val blank = parse("refundOf" to str(""), "splitId" to str("  "))
        assertNull(blank.refundOf)
        assertNull(blank.splitId)
    }
}
