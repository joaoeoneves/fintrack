package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
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

/**
 * Unit tests for [FirestoreBudgetRepository]'s private `toBudgetOrNull()` conversion (restructured
 * from an if/return chain into a `when` expression to satisfy detekt's ReturnCount rule -- same drop
 * behavior, now logged). See [FirestoreExpenseRepositoryTest] for the full rationale behind this
 * reflection + real-`DocumentSnapshot` approach.
 *
 * Note: unlike expense/income, a budget's category comes from the *document id* (not a field), so
 * the "malformed category" case here is an id that doesn't match any [ExpenseCategory] name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FirestoreBudgetRepositoryTest {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var repository: FirestoreBudgetRepository

    private companion object {
        private const val TAG = "FirestoreBudgetRepo"
    }

    @Before
    fun setUp() {
        ShadowLog.clear()
        val app = FirebaseTestApp.ensureInitialized(RuntimeEnvironment.getApplication())
        firestore = FirebaseFirestore.getInstance(app)
        repository = FirestoreBudgetRepository(firestore, FirebaseAuth.getInstance(app))
    }

    private fun toBudgetOrNull(snapshot: DocumentSnapshot): Budget? {
        val method = FirestoreBudgetRepository::class.java.getDeclaredMethod("toBudgetOrNull", DocumentSnapshot::class.java)
        method.isAccessible = true
        return method.invoke(repository, snapshot) as Budget?
    }

    @Test
    fun wellFormedDoc_parsesToBudget_andIsNotDropped() {
        val snapshot =
            TestDocumentSnapshots.found(
                firestore,
                id = ExpenseCategory.SHOPPING.name,
                fields = mapOf("limitCents" to TestDocumentSnapshots.longValue(10_000L)),
                collection = "budgets",
            )

        val result = toBudgetOrNull(snapshot)

        assertEquals(Budget(ExpenseCategory.SHOPPING, 10_000L), result)
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty())
    }

    @Test
    fun malformedDoc_idNotAValidCategory_isDroppedAsNull_andLogsWarning() {
        val snapshot =
            TestDocumentSnapshots.found(
                firestore,
                id = "NOT_A_REAL_CATEGORY",
                fields = mapOf("limitCents" to TestDocumentSnapshots.longValue(10_000L)),
                collection = "budgets",
            )

        val result = toBudgetOrNull(snapshot)

        assertNull(result)
        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue("expected a warning log for invalid 'category', got: $logs", logs.any { it.msg.contains("'category'") })
    }

    @Test
    fun malformedDoc_missingLimitCents_isDroppedAsNull_andLogsWarning() {
        val snapshot =
            TestDocumentSnapshots.found(
                firestore,
                id = ExpenseCategory.RECURRING.name,
                fields = emptyMap(),
                collection = "budgets",
            )

        val result = toBudgetOrNull(snapshot)

        assertNull(result)
        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue("expected a warning log for missing 'limitCents', got: $logs", logs.any { it.msg.contains("'limitCents'") })
    }
}
