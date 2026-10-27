package com.joaoeoneves.fintrack.ui.insights

import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.model.GroupProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [InsightsBreakdown.hasGroups]. */
class InsightsBreakdownTest {
    private fun breakdown(groups: List<GroupProgress>) = InsightsBreakdown(emptyList(), emptyList(), false, groups)

    private fun row(group: CategoryGroup?) = GroupProgress(group, planned = 0L, spent = 0L, categories = emptyList())

    @Test
    fun default_hasNoGroups() {
        assertFalse(InsightsBreakdown(emptyList(), emptyList(), hasExpenses = false).hasGroups)
    }

    @Test
    fun onlyOtherEntry_hasNoGroups() {
        assertFalse(breakdown(listOf(row(null))).hasGroups)
    }

    @Test
    fun anyRealGroup_hasGroups() {
        assertTrue(breakdown(listOf(row(CategoryGroup("g1", "Food", 0)), row(null))).hasGroups)
    }
}
