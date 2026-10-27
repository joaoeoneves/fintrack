package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.joaoeoneves.fintrack.domain.model.Earmark
import com.joaoeoneves.fintrack.domain.model.IncomeBasis
import com.joaoeoneves.fintrack.domain.model.IncomeFrequency
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import org.robolectric.shadows.ShadowLog
import java.time.YearMonth

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
        extra: Map<String, com.google.firestore.v1.Value> = emptyMap(),
    ) = buildMap {
        putAll(extra)
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

        assertEquals(
            setOf(
                "label",
                "amountCents",
                "order",
                "basis",
                "frequency",
                "monthOfYear",
                "yearMonth",
                "skipMonths",
                "updatedAt",
            ),
            map.keys,
        )
        assertEquals("Meal allowance", map["label"])
        assertEquals(20_000L, map["amountCents"])
        assertEquals(1, map["order"])
        assertEquals(FieldValue.serverTimestamp(), map["updatedAt"])
    }

    private fun PlannedIncome.toFirestoreMapForTest(): Map<String, Any?> = toFirestoreMap()

    private fun str(value: String) = TestDocumentSnapshots.stringValue(value)

    @Test
    fun missingV2Fields_mapToFixedMonthlyDefaults() {
        val result = toPlannedIncomeOrNull(snapshot("a", fields()))

        assertEquals(IncomeBasis.FIXED, result?.basis)
        assertEquals(IncomeFrequency.MONTHLY, result?.frequency)
        assertNull(result?.monthOfYear)
        assertNull(result?.yearMonth)
        assertEquals(emptySet<Int>(), result?.skipMonths)
    }

    @Test
    fun perWorkingDayBasis_parses() {
        val result = toPlannedIncomeOrNull(snapshot("a", fields(extra = mapOf("basis" to str("PER_WORKING_DAY")))))

        assertEquals(IncomeBasis.PER_WORKING_DAY, result?.basis)
    }

    @Test
    fun yearlyWithMonth_parses() {
        val extra = mapOf("frequency" to str("YEARLY"), "monthOfYear" to TestDocumentSnapshots.longValue(7))

        val result = toPlannedIncomeOrNull(snapshot("a", fields(extra = extra)))

        assertEquals(IncomeFrequency.YEARLY, result?.frequency)
        assertEquals(7, result?.monthOfYear)
    }

    @Test
    fun onceWithYearMonth_parses() {
        val extra = mapOf("frequency" to str("ONCE"), "yearMonth" to str("2026-12"))

        val result = toPlannedIncomeOrNull(snapshot("a", fields(extra = extra)))

        assertEquals(IncomeFrequency.ONCE, result?.frequency)
        assertEquals(YearMonth.of(2026, 12), result?.yearMonth)
    }

    @Test
    fun unknownBasis_isDropped() {
        assertNull(toPlannedIncomeOrNull(snapshot("a", fields(extra = mapOf("basis" to str("HOURLY"))))))
    }

    @Test
    fun unknownFrequency_isDropped() {
        assertNull(toPlannedIncomeOrNull(snapshot("a", fields(extra = mapOf("frequency" to str("WEEKLY"))))))
    }

    @Test
    fun yearlyWithoutMonthOfYear_isDropped() {
        assertNull(toPlannedIncomeOrNull(snapshot("a", fields(extra = mapOf("frequency" to str("YEARLY"))))))
    }

    @Test
    fun onceWithoutOrWithUnparsableYearMonth_isDropped() {
        val once = mapOf("frequency" to str("ONCE"))
        assertNull(toPlannedIncomeOrNull(snapshot("a", fields(extra = once))))
        assertNull(toPlannedIncomeOrNull(snapshot("a", fields(extra = once + ("yearMonth" to str("2026-13"))))))
    }

    @Test
    fun toFirestoreMap_writesScheduleFields() {
        val line =
            PlannedIncome(
                "x",
                "Bonus",
                5L,
                0,
                IncomeBasis.PER_WORKING_DAY,
                IncomeFrequency.ONCE,
                yearMonth = YearMonth.of(2026, 12),
                skipMonths = setOf(9, 3),
            )

        val map = line.toFirestoreMapForTest()

        assertEquals("PER_WORKING_DAY", map["basis"])
        assertEquals("ONCE", map["frequency"])
        assertEquals("2026-12", map["yearMonth"])
        assertNull(map["monthOfYear"])
        assertEquals(listOf(3, 9), map["skipMonths"])
    }

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

    @Test
    fun toFirestoreMap_unearmarked_omitsEarmarkFields() {
        val map = PlannedIncome("x", "Pay", 5L, 0).toFirestoreMapForTest()

        assertFalse(map.containsKey("earmarkType"))
        assertFalse(map.containsKey("earmarkId"))
    }

    @Test
    fun toFirestoreMap_earmarked_writesTypeAndId() {
        val group = PlannedIncome("x", "Pay", 5L, 0, earmark = Earmark.Group("g1")).toFirestoreMapForTest()
        val category = PlannedIncome("x", "Pay", 5L, 0, earmark = Earmark.Category("FOOD")).toFirestoreMapForTest()

        assertEquals("GROUP", group["earmarkType"])
        assertEquals("g1", group["earmarkId"])
        assertEquals("CATEGORY", category["earmarkType"])
        assertEquals("FOOD", category["earmarkId"])
    }

    @Test
    fun earmarkFields_parse() {
        val extra = mapOf("earmarkType" to str("GROUP"), "earmarkId" to str("g1"))

        assertEquals(Earmark.Group("g1"), toPlannedIncomeOrNull(snapshot("a", fields(extra = extra)))?.earmark)
    }

    @Test
    fun earmarkMissingOrHalfPresentOrUnknown_parsesAsUnearmarked() {
        val half = mapOf("earmarkType" to str("GROUP"))
        val unknown = mapOf("earmarkType" to str("WALLET"), "earmarkId" to str("w"))

        assertNull(toPlannedIncomeOrNull(snapshot("a", fields()))?.earmark)
        assertNull(toPlannedIncomeOrNull(snapshot("a", fields(extra = half)))?.earmark)
        assertNull(toPlannedIncomeOrNull(snapshot("a", fields(extra = unknown)))?.earmark)
    }
}
