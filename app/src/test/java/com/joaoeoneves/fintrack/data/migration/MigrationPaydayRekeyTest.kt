package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import org.junit.Assert.assertEquals
import org.junit.Test

/** A guest `pay_<line>_<yyyyMM>` income follows its line onto the account's label-matched line. */
class MigrationPaydayRekeyTest {
    private val guestLine = localPlanned("g1", label = "Net  Salary")
    private val remote =
        remoteSnapshot(
            plannedIncomeIds = setOf("acct"),
            plannedIncomeLabels = setOf("net salary"),
        ).copy(plannedIncomeIdsByLabel = mapOf("net salary" to "acct"))

    private fun docWith(vararg ids: String): LocalDataDocument {
        val incomes = ids.map { localIncome(it) }
        return localDoc(income = incomes).copy(plannedIncome = listOf(guestLine))
    }

    @Test
    fun labelMatchedLine_itsPaydayIncomeIsRekeyedToTheAccountLine() {
        val plan = planMigration(docWith("pay_g1_202610", "manual"), remote)

        assertEquals(listOf("pay_acct_202610", "manual"), plan.income.map { it.id })
        // Only the label-matched guest line itself is skipped (it is not copied).
        assertEquals(1, plan.skipped)
        assertEquals(emptyList<String>(), plan.plannedIncome.map { it.id })
    }

    @Test
    fun rekeyedIncomeThatAlreadyExistsInTheAccount_isSkippedAndCounted() {
        val withReceipt = remote.copy(incomeIds = setOf("pay_acct_202610"))

        val plan = planMigration(docWith("pay_g1_202610", "pay_g1_202611"), withReceipt)

        assertEquals(listOf("pay_acct_202611"), plan.income.map { it.id })
        assertEquals(2, plan.skipped) // the line, plus the already-recorded receipt
    }

    @Test
    fun twoGuestIncomesCollapsingOntoOneId_keepOnlyOne() {
        val twoLines = listOf(guestLine, localPlanned("g2", label = "NET salary"))
        val incomes = listOf(localIncome("pay_g1_202610"), localIncome("pay_g2_202610"))
        val doc = localDoc(income = incomes).copy(plannedIncome = twoLines)

        val plan = planMigration(doc, remote)

        assertEquals(listOf("pay_acct_202610"), plan.income.map { it.id })
        assertEquals(3, plan.skipped) // both lines, plus the duplicate receipt
    }

    @Test
    fun paydayIncomeOfAnUnmatchedLine_keepsItsId() {
        val plan = planMigration(docWith("pay_other_202610"), remote)

        assertEquals(listOf("pay_other_202610"), plan.income.map { it.id })
    }
}
