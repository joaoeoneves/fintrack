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
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The 5.4 `needsReview` field: written only when true on create, deleted explicitly on update (updates merge, so
 * an absent key would leave a stale flag), read as false when absent or not a boolean.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreReviewFlagTest {
    private lateinit var firestore: FirebaseFirestore
    private val date = Instant.parse("2026-10-01T12:00:00Z")

    @Before
    fun setUp() {
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
    }

    private fun expense(review: Boolean): Expense {
        val base = Expense("e1", "Rent", 500, BuiltInCategories.FOOD, date, null)
        return base.copy(needsReview = review)
    }

    private fun income(review: Boolean) = Income("i1", "Pay", 500, date, null, needsReview = review)

    private fun isDelete(value: Any?) = value == FieldValue.delete()

    private fun parse(
        facade: String,
        function: String,
        collection: String,
        fields: Map<String, Value>,
    ): Any? {
        val snapshot = TestDocumentSnapshots.found(firestore, "x1", fields, collection = collection)
        val method =
            Class
                .forName("com.joaoeoneves.fintrack.data.$facade")
                .getDeclaredMethod(function, DocumentSnapshot::class.java)
        method.isAccessible = true
        return method.invoke(null, snapshot)
    }

    private fun parseExpense(vararg extra: Pair<String, Value>) =
        parse(
            "FirestoreExpenseRepositoryKt",
            "toExpenseOrNull",
            "expenses",
            mapOf(
                "name" to TestDocumentSnapshots.stringValue("Rent"),
                "amountCents" to TestDocumentSnapshots.longValue(500),
                "category" to TestDocumentSnapshots.stringValue("FOOD"),
                "date" to TestDocumentSnapshots.timestampValue(Timestamp(date)),
                *extra,
            ),
        ) as Expense

    private fun parseIncome(vararg extra: Pair<String, Value>) =
        parse(
            "FirestoreIncomeRepositoryKt",
            "toIncomeOrNull",
            "income",
            mapOf(
                "source" to TestDocumentSnapshots.stringValue("Pay"),
                "amountCents" to TestDocumentSnapshots.longValue(500),
                "date" to TestDocumentSnapshots.timestampValue(Timestamp(date)),
                *extra,
            ),
        ) as Income

    @Test
    fun expense_create_writesTheKeyOnlyWhenTrue() {
        assertEquals(true, expense(true).toFirestoreMap(includeCreatedAt = true)["needsReview"])
        assertFalse(expense(false).toFirestoreMap(includeCreatedAt = true).containsKey("needsReview"))
    }

    @Test
    fun expense_update_deletesTheKeyWhenFalse_andWritesTrue() {
        assertEquals(true, expense(true).toFirestoreMap(includeCreatedAt = false)["needsReview"])
        assertTrue(isDelete(expense(false).toFirestoreMap(includeCreatedAt = false)["needsReview"]))
    }

    @Test
    fun income_create_writesTheKeyOnlyWhenTrue() {
        assertEquals(true, income(true).toFirestoreMap(includeCreatedAt = true)["needsReview"])
        assertFalse(income(false).toFirestoreMap(includeCreatedAt = true).containsKey("needsReview"))
    }

    @Test
    fun income_update_deletesTheKeyWhenFalse_andWritesTrue() {
        assertEquals(true, income(true).toFirestoreMap(includeCreatedAt = false)["needsReview"])
        assertTrue(isDelete(income(false).toFirestoreMap(includeCreatedAt = false)["needsReview"]))
    }

    @Test
    fun expense_read_trueFlag_oldDocWithoutKey_andNonBoolean() {
        assertTrue(parseExpense("needsReview" to TestDocumentSnapshots.booleanValue(true)).needsReview)
        assertFalse(parseExpense("needsReview" to TestDocumentSnapshots.booleanValue(false)).needsReview)
        assertFalse(parseExpense().needsReview)
        assertFalse(parseExpense("needsReview" to TestDocumentSnapshots.stringValue("true")).needsReview)
    }

    @Test
    fun income_read_trueFlag_oldDocWithoutKey_andNonBoolean() {
        assertTrue(parseIncome("needsReview" to TestDocumentSnapshots.booleanValue(true)).needsReview)
        assertFalse(parseIncome().needsReview)
        assertFalse(parseIncome("needsReview" to TestDocumentSnapshots.stringValue("true")).needsReview)
    }
}
