package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.google.firestore.v1.Value
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.YearMonth

/** 5.5: envelope and month-settings documents written by older versions map without the removed fields. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreSimplifiedMappingTest {
    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setUp() {
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
    }

    private fun str(v: String) = TestDocumentSnapshots.stringValue(v)

    private fun lng(v: Long) = TestDocumentSnapshots.longValue(v)

    private fun doc(
        id: String,
        collection: String,
        fields: Map<String, Value>,
    ): DocumentSnapshot = TestDocumentSnapshots.found(firestore, id, fields, collection)

    private fun call(
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

    private fun envelope(fields: Map<String, Value>) =
        call("FirestoreEnvelopeRepositoryKt", "toEnvelopeOrNull", doc("g1", "categoryGroups", fields)) as Envelope?

    private fun monthSettings(
        id: String,
        fields: Map<String, Value>,
    ) = call("FirestoreMonthSettingsRepositoryKt", "toMonthSettingsOrNull", doc(id, "monthSettings", fields))
        as MonthSettings?

    @Test
    fun envelope_legacyAmountFundingAndTimingFields_areIgnored() {
        val legacy =
            mapOf(
                "name" to str("Food"),
                "order" to lng(2L),
                "amountCents" to lng(5_000L),
                "fundedBy" to str("meal"),
                "startMonth" to str("2026-03"),
            )

        assertEquals(Envelope("g1", "Food", 2), envelope(legacy))
    }

    @Test
    fun envelope_toFirestoreMap_writesOnlyNameOrderAndTimestamp() {
        val map = Envelope("g1", "Food", 2).toFirestoreMap()

        assertEquals(setOf("name", "order", "updatedAt"), map.keys)
        assertEquals(FieldValue.serverTimestamp(), map["updatedAt"])
    }

    @Test
    fun monthSettings_documentWithOnlyTheRemovedWorkingDays_isIgnored() {
        assertNull(monthSettings("2026-10", mapOf("workingDays" to lng(15L))))
    }

    @Test
    fun monthSettings_workingDaysAndDaysOff_keepOnlyDaysOff() {
        val parsed = monthSettings("2026-10", mapOf("workingDays" to lng(15L), "daysOff" to lng(3L)))

        assertEquals(MonthSettings(YearMonth.of(2026, 10), 3), parsed)
    }

    @Test
    fun monthSettings_zeroDaysOff_isIgnored_andBadIdIsDropped() {
        assertNull(monthSettings("2026-10", mapOf("daysOff" to lng(0L))))
        assertNull(monthSettings("not-a-month", mapOf("daysOff" to lng(2L))))
    }

    @Test
    fun monthSettings_toFirestoreMap_hasNoWorkingDaysField() {
        val map = MonthSettings(YearMonth.of(2026, 10), 3).toFirestoreMap()

        assertEquals(3, map["daysOff"])
        assertFalse(map.containsKey("workingDays"))
    }
}
