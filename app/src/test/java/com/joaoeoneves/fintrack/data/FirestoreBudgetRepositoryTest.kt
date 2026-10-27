package com.joaoeoneves.fintrack.data

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.TestDocumentSnapshots
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
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
 * any document id is kept (built-in or user category); only a missing `limitCents` drops a doc.
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
        val method =
            FirestoreBudgetRepository::class.java
                .getDeclaredMethod("toBudgetOrNull", DocumentSnapshot::class.java)
        method.isAccessible = true
        return method.invoke(repository, snapshot) as Budget?
    }

    @Test
    fun wellFormedDoc_parsesToBudget_andIsNotDropped() {
        val snapshot =
            TestDocumentSnapshots.found(
                firestore,
                id = BuiltInCategories.SHOPPING.id.value,
                fields = mapOf("limitCents" to TestDocumentSnapshots.longValue(10_000L)),
                collection = "budgets",
            )

        val result = toBudgetOrNull(snapshot)

        assertEquals(Budget(BuiltInCategories.SHOPPING.id, 10_000L), result)
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty())
    }

    @Test
    fun foodDocId_parsesToFoodBudget() {
        val snapshot =
            TestDocumentSnapshots.found(
                firestore,
                id = "FOOD",
                fields = mapOf("limitCents" to TestDocumentSnapshots.longValue(25_000L)),
                collection = "budgets",
            )

        assertEquals(Budget(BuiltInCategories.FOOD.id, 25_000L), toBudgetOrNull(snapshot))
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty())
    }

    @Test
    fun unknownDocId_groceries_isKept_asBudgetForThatId_withoutWarning() {
        val snapshot =
            TestDocumentSnapshots.found(
                firestore,
                id = "GROCERIES",
                fields = mapOf("limitCents" to TestDocumentSnapshots.longValue(25_000L)),
                collection = "budgets",
            )

        assertEquals(Budget(CategoryId("GROCERIES"), 25_000L), toBudgetOrNull(snapshot))
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty())
    }

    @Test
    fun generatedUserCategoryDocId_isKept() {
        // A budget for a user-defined category: its doc id is the category's generated id.
        val snapshot =
            TestDocumentSnapshots.found(
                firestore,
                id = "aB3xYz09QwErTy",
                fields = mapOf("limitCents" to TestDocumentSnapshots.longValue(10_000L)),
                collection = "budgets",
            )

        assertEquals(Budget(CategoryId("aB3xYz09QwErTy"), 10_000L), toBudgetOrNull(snapshot))
    }

    @Test
    fun malformedDoc_missingLimitCents_isDroppedAsNull_andLogsWarning() {
        val snapshot =
            TestDocumentSnapshots.found(
                firestore,
                id = BuiltInCategories.RECURRING.id.value,
                fields = emptyMap(),
                collection = "budgets",
            )

        val result = toBudgetOrNull(snapshot)

        assertNull(result)
        val logs = ShadowLog.getLogsForTag(TAG)
        assertTrue(
            "expected a warning log for missing 'limitCents', got: $logs",
            logs.any { it.msg.contains("'limitCents'") },
        )
    }
}
