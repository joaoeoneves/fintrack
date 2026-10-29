package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalExpense
import com.joaoeoneves.fintrack.data.local.LocalIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The guest to account migration carries the 5.4 review flag and the dismissed suggestions. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigrationReviewFieldsTest {
    private val at = "2026-10-01T12:00:00Z"
    private val flagged = LocalExpense("e1", "Rent", 500, "FOOD", at, needsReview = true)
    private val reviewed = LocalExpense("e2", "Lunch", 500, "FOOD", at)
    private val flaggedIncome = LocalIncome("i1", "Pay", 500, at, needsReview = true)

    private fun flaggedDoc() = localDoc(expenses = listOf(flagged, reviewed), income = listOf(flaggedIncome))

    private fun writes(plan: MigrationPlan) = plan.toBatches().flatten().groupBy { it.collection }

    @Test
    fun planner_keepsTheFlagOnExpensesAndIncome() {
        val plan = planMigration(flaggedDoc(), remoteSnapshot())

        assertEquals(listOf(true, false), plan.expenses.map { it.needsReview })
        assertTrue(plan.income.single().needsReview)
    }

    @Test
    fun pendingWrite_writesTheKeyOnlyWhenFlagged() {
        val plan = planMigration(flaggedDoc(), remoteSnapshot())

        val byId = writes(plan).getValue("expenses").associateBy { it.id }
        assertEquals(true, byId.getValue("e1").data["needsReview"])
        assertFalse(byId.getValue("e2").data.containsKey("needsReview"))
        assertEquals(true, writes(plan).getValue("income").single().data["needsReview"])
    }

    @Test
    fun dismissals_areCleaned_countAsOneWrite_andAreWrittenAsAMerge() {
        val local = localDoc().copy(dismissedRecurringSuggestions = listOf("netflix", " ", "netflix", "gym"))

        val plan = planMigration(local, remoteSnapshot())

        assertEquals(listOf("netflix", "gym"), plan.dismissedRecurring)
        assertEquals(1, plan.total)
        val write = writes(plan).getValue("settings").single()
        assertEquals("suggestions", write.id)
        assertTrue(write.merge)
        assertTrue(write.data.containsKey("dismissedRecurring"))
        assertTrue(write.data.containsKey("updatedAt"))
    }

    @Test
    fun noDismissals_writeNothingToSettings_andOtherWritesDoNotMerge() {
        val plan = planMigration(localDoc(expenses = listOf(flagged)), remoteSnapshot())

        assertFalse(writes(plan).containsKey("settings"))
        assertTrue(plan.toBatches().flatten().none { it.merge })
    }

    @Test
    fun suggestionsWrite_isTheLastBatch() {
        val local = localDoc(expenses = listOf(flagged)).copy(dismissedRecurringSuggestions = listOf("gym"))

        val batches = planMigration(local, remoteSnapshot()).toBatches()

        assertEquals("settings", batches.last().single().collection)
    }
}
