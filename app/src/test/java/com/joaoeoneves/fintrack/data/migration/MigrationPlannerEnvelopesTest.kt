package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.domain.model.Envelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The envelope amount and funding line in [planMigration]. */
class MigrationPlannerEnvelopesTest {
    private fun doc(vararg groups: LocalCategoryGroup) =
        localDoc().copy(categoryGroups = groups.toList(), plannedIncome = listOf(localPlanned("meal")))

    @Test
    fun amountAndFundingLine_areCopied_whenTheLinesAreCopiedToo() {
        val plan = planMigration(doc(LocalCategoryGroup("g1", "Food", 0, 5_000, "meal")), remoteSnapshot())

        val written = plan.categoryGroups.single()
        assertEquals(5_000L, written.amountCents)
        assertEquals("meal", written.fundedByLineId)
    }

    @Test
    fun fundingLine_isDropped_whenTheAccountAlreadyHasThatLine_amountKept() {
        val plan =
            planMigration(
                doc(LocalCategoryGroup("g1", "Food", 0, 5_000, "meal")),
                remoteSnapshot(plannedIncomeIds = setOf("meal")),
            )

        val written = plan.categoryGroups.single()
        assertNull(written.fundedByLineId)
        assertEquals(5_000L, written.amountCents)
    }

    @Test
    fun fundingLine_pointingToANonCopiedLine_isDropped() {
        val plan = planMigration(doc(LocalCategoryGroup("g1", "Food", 0, null, "gone")), remoteSnapshot())

        assertNull(plan.categoryGroups.single().fundedByLineId)
    }

    @Test
    fun nameMerge_remoteEnvelopeWins_localAmountAndFundingAreNotWritten() {
        val remote = remoteSnapshot(categoryGroups = listOf(Envelope("acct", "Food", 0, 9_000, "acct-line")))

        val plan = planMigration(doc(LocalCategoryGroup("g1", " food ", 0, 5_000, "meal")), remote)

        assertEquals(emptyList<String>(), plan.categoryGroups.map { it.id })
    }
}
