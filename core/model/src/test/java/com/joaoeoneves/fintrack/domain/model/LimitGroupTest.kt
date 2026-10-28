package com.joaoeoneves.fintrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitGroupTest {
    private val food = CategoryGroup("g1", "Food", 0)
    private val fun1 = CategoryGroup("g2", "Fun", 1)

    private fun cat(
        id: String,
        groupId: String?,
    ) = Category(CategoryId(id), id, "icon", "color", CategoryKind.SPENDING, 0, false, false, groupId)

    private fun row(
        id: String,
        groupId: String?,
        planned: Long? = null,
        spent: Long = 0,
    ) = CategoryBudget(cat(id, groupId), planned, spent)

    private fun progress(vararg rows: CategoryBudget) = rows.toList()

    private fun groupsOf(vararg groups: CategoryGroup) = groups.map { GroupProgress(it, 0, 0, emptyList()) }

    @Test
    fun groupsRows_byGroupId_inGroupOrder() {
        val a = row("a", "g1", planned = 100)
        val b = row("b", "g2", planned = 50)
        val c = row("c", "g1", planned = 20)

        val result = limitGroupsOf(listOf(a, b, c), emptyList(), groupsOf(food, fun1))

        assertEquals(listOf(food, fun1), result.map { it.group })
        assertEquals(listOf("a", "c"), result[0].rows.map { it.category.id.value })
        assertEquals(120, result[0].plannedCents)
        assertEquals(50, result[1].plannedCents)
    }

    @Test
    fun unknownGroupAndNoGroup_goToUngroupedLast() {
        val rows = listOf(row("a", "g1"), row("b", "missing"), row("c", null))

        val result = limitGroupsOf(rows, emptyList(), groupsOf(food))

        assertEquals(listOf(food, null), result.map { it.group })
        assertEquals(listOf("b", "c"), result.last().rows.map { it.category.id.value })
    }

    @Test
    fun spentIsMergedFromProgress_andFallsBackToTheRowsOwn() {
        val a = row("a", "g1", planned = 100, spent = 1)
        val b = row("b", "g1", planned = 100, spent = 7)

        val result = limitGroupsOf(listOf(a, b), progress(row("a", "g1", spent = 60)), groupsOf(food))

        assertEquals(listOf(60L, 7L), result.single().rows.map { it.spentCents })
        assertEquals(67, result.single().spentCents)
        assertEquals(200, result.single().plannedCents)
    }

    @Test
    fun emptyGroup_keepsItsHeader() {
        val result = limitGroupsOf(listOf(row("a", "g1")), emptyList(), groupsOf(food, fun1))

        val empty = result.single { it.group == fun1 }
        assertTrue(empty.rows.isEmpty())
        assertEquals(0, empty.plannedCents)
        assertEquals(0, empty.spentCents)
    }

    @Test
    fun noUngroupedCategories_noOtherGroup() {
        val result = limitGroupsOf(listOf(row("a", "g1")), emptyList(), groupsOf(food))

        assertEquals(1, result.size)
        assertTrue(result.none { it.group == null })
    }

    @Test
    fun noGroups_everythingIsUngrouped() {
        val result = limitGroupsOf(listOf(row("a", "g1"), row("b", null)), emptyList(), emptyList())

        assertNull(result.single().group)
        assertEquals(2, result.single().rows.size)
    }

    @Test
    fun noCategoryAppearsTwice_andPlannedNullCountsAsZero() {
        val rows = listOf(row("a", "g1", planned = 10), row("b", "g2"), row("c", null), row("d", "zzz"))

        val result = limitGroupsOf(rows, emptyList(), groupsOf(food, fun1))

        val ids = result.flatMap { g -> g.rows.map { it.category.id.value } }
        assertEquals(ids.toSet().size, ids.size)
        assertEquals(setOf("a", "b", "c", "d"), ids.toSet())
        assertEquals(0, result[1].plannedCents)
    }

    @Test
    fun emptyInputs_noGroups() {
        assertTrue(limitGroupsOf(emptyList(), emptyList(), emptyList()).isEmpty())
    }
}
