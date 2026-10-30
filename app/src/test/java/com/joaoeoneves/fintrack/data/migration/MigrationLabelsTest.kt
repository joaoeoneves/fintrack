package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalCategory
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [planMigration] matching by label (income lines), by name and kind (categories). */
class MigrationLabelsTest {
    private fun lines(vararg labels: String) = labels.mapIndexed { i, l -> localPlanned("p$i", label = l) }

    private fun saving(
        id: String,
        name: String,
    ) = LocalCategory(id, name, "home", "teal", "SAVING", 0)

    @Test
    fun guestLine_withALabelMissingFromTheAccount_isCopied() {
        val local = localDoc().copy(plannedIncome = lines("Bonus"))

        val remote = remoteSnapshot(plannedIncomeIds = setOf("x"), plannedIncomeLabels = setOf("Salary"))

        val plan = planMigration(local, remote)

        assertEquals(listOf("Bonus"), plan.plannedIncome.map { it.label })
        assertEquals(0, plan.skipped)
    }

    @Test
    fun duplicateLabel_differingOnlyInCaseOrAccent_isSkippedAndCounted() {
        val local = localDoc().copy(plannedIncome = lines("SUBSÍDIO  de natal", "Bonus"))

        val plan = planMigration(local, remoteSnapshot(plannedIncomeLabels = setOf("subsidio de Natal")))

        assertEquals(listOf("Bonus"), plan.plannedIncome.map { it.label })
        assertEquals(1, plan.skipped)
    }

    @Test
    fun categoryWithTheSameNameButAnotherKind_isNotMerged() {
        val local = localDoc(categories = listOf(saving("local-gym", "Gym")))
        val remote = remoteSnapshot(categories = listOf(userCategory("acct-gym", "gym")))

        val plan = planMigration(local, remote)

        assertEquals(listOf("local-gym"), plan.categories.map { it.id })
    }

    @Test
    fun categoryWithTheSameNameAndKind_isMergedIntoTheAccountOne() {
        val local = localDoc(categories = listOf(localCategory("local-gym", "Gym")))
        val remote = remoteSnapshot(categories = listOf(userCategory("acct-gym", "gym")))

        val plan = planMigration(local, remote)

        assertTrue(plan.categories.isEmpty())
    }


}
