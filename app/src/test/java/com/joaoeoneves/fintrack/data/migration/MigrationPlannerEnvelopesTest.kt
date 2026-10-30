package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.domain.model.Envelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Envelopes in [planMigration]: name and order only, merged by name. */
class MigrationPlannerEnvelopesTest {
    private fun doc(vararg groups: LocalCategoryGroup) = localDoc().copy(categoryGroups = groups.toList())

    @Test
    fun envelope_isCopiedWithNameAndOrderOnly() {
        val plan = planMigration(doc(LocalCategoryGroup("g1", "Food", 3)), remoteSnapshot())

        assertEquals(listOf("g1"), plan.categoryGroups.map { it.id })
        assertEquals("Food", plan.categoryGroups.single().name)
        val write = plan.toBatches().flatten().single { it.collection == "categoryGroups" }
        assertFalse(write.data.keys.any { it in setOf("amountCents", "fundedBy", "fundedByLineId", "startMonth", "versions") })
    }

    @Test
    fun nameMerge_remoteEnvelopeWins_localOneIsNotWritten() {
        val remote = remoteSnapshot(categoryGroups = listOf(Envelope("acct", "Food", 0)))

        val plan = planMigration(doc(LocalCategoryGroup("g1", " food ", 0)), remote)

        assertEquals(emptyList<String>(), plan.categoryGroups.map { it.id })
    }
}
