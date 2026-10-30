package com.joaoeoneves.fintrack.ui.activity.analytics

import com.joaoeoneves.fintrack.domain.model.CategoryTotal
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [groupTotalsOf]: the spending breakdown summed per group. */
class GroupTotalsOfTest {
    private val food = Envelope("g-food", "Food", 0)
    private val home = Envelope("g-home", "Home", 1)

    private fun total(
        id: String,
        cents: Long,
        group: String?,
    ) = CategoryTotal(userCategory(id).copy(groupId = group), cents)

    @Test
    fun sumsCategoriesPerGroup_biggestFirst_noGroupLast() {
        val totals =
            listOf(
                total("a", 1_000, "g-food"),
                total("b", 2_500, "g-home"),
                total("c", 700, "g-food"),
                total("d", 9_000, null),
            )

        val result = groupTotalsOf(totals, listOf(food, home), "None")

        val expected =
            listOf(
                GroupTotal("g-home", "Home", 2_500),
                GroupTotal("g-food", "Food", 1_700),
                GroupTotal("", "None", 9_000),
            )
        assertEquals(expected, result)
    }

    @Test
    fun unknownGroupId_countsAsNoGroup() {
        val result = groupTotalsOf(listOf(total("a", 500, "gone"), total("b", 100, null)), listOf(food), "None")

        assertEquals(listOf(GroupTotal("", "None", 600)), result)
    }

    @Test
    fun groupsWithNothingSpent_areDropped() {
        val result = groupTotalsOf(listOf(total("a", 300, "g-food")), listOf(food, home), "None")

        assertEquals(listOf(GroupTotal("g-food", "Food", 300)), result)
    }

    @Test
    fun noSpending_isEmpty() {
        assertTrue(groupTotalsOf(emptyList(), listOf(food), "None").isEmpty())
    }
}
