package com.joaoeoneves.fintrack.data.migration

import android.app.Application
import com.joaoeoneves.fintrack.data.local.LocalExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The guest to account migration keeps expense ids, so `refundOf` / `splitId` keep pointing at the right record. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigrationExpenseLinksTest {
    private val at = "2026-10-01T12:00:00Z"
    private val orig = LocalExpense("orig", "Shoes", 5000, "FOOD", at)
    private val refund = LocalExpense("ref", "Return", 2000, "FOOD", at, isRefund = true, refundOf = "orig")
    private val part = LocalExpense("part", "Shop", 300, "FOOD", at, splitId = "grp")

    private fun writes() =
        planMigration(localDoc(expenses = listOf(orig, refund, part)), remoteSnapshot())
            .toBatches()
            .flatten()
            .filter { it.collection == "expenses" }
            .associateBy { it.id }

    @Test
    fun planner_keepsLinksAndIds() {
        val plan = planMigration(localDoc(expenses = listOf(orig, refund, part)), remoteSnapshot())

        assertEquals(listOf("orig", "ref", "part"), plan.expenses.map { it.id })
        assertEquals(listOf(null, "orig", null), plan.expenses.map { it.refundOf })
        assertEquals(listOf(null, null, "grp"), plan.expenses.map { it.splitId })
    }

    @Test
    fun pendingWrites_useTheSameDocumentIds_andWriteTheKeysOnlyWhenSet() {
        val byId = writes()

        assertEquals("orig", byId.getValue("ref").data["refundOf"])
        assertEquals("grp", byId.getValue("part").data["splitId"])
        assertFalse(byId.getValue("orig").data.containsKey("refundOf"))
        assertFalse(byId.getValue("orig").data.containsKey("splitId"))
        assertFalse(byId.getValue("part").data.containsKey("refundOf"))
    }

    @Test
    fun invalidLink_isLeftOutOfThePlan_butDanglingOneIsMigrated() {
        val notRefund = LocalExpense("bad", "X", 5, "FOOD", at, refundOf = "orig")
        val dangling = LocalExpense("dang", "R", 5, "FOOD", at, isRefund = true, refundOf = "deleted")

        val plan = planMigration(localDoc(expenses = listOf(orig, notRefund, dangling)), remoteSnapshot())

        assertEquals(listOf("orig", "dang"), plan.expenses.map { it.id })
    }
}
