package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalCategoryGroup
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category groups in [planMigration] and [toBatches]. */
class MigrationPlannerGroupsTest {
    private val food = LocalCategoryGroup("g-food", "Food", 0)
    private val leisure = LocalCategoryGroup("g-fun", "Fun", 1)

    private fun docWith(
        groups: List<LocalCategoryGroup>,
        vararg categoryGroups: Pair<String, String?>,
    ) = localDoc(
        categories = categoryGroups.mapIndexed { i, (id, g) -> localCategory(id, order = i).copy(groupId = g) },
    ).copy(categoryGroups = groups)

    @Test
    fun newGroups_keepIdsAndAreOrderedAfterTheAccountsGroups() {
        val remote = remoteSnapshot(categoryGroups = listOf(Envelope("r1", "Rent", 4)))

        val plan = planMigration(docWith(listOf(leisure, food)), remote)

        // Written in the local order (food 0, fun 1), numbered after the account's highest order (4).
        assertEquals(listOf("g-food", "g-fun"), plan.categoryGroups.map { it.id })
        assertEquals(listOf(5, 6), plan.categoryGroups.map { it.order })
        assertEquals(2, plan.total)
    }

    @Test
    fun emptyAccount_groupsStartAtOrderZero() {
        val plan = planMigration(docWith(listOf(food, leisure)), remoteSnapshot())

        assertEquals(listOf(0, 1), plan.categoryGroups.map { it.order })
    }

    @Test
    fun nameCollision_reusesAccountGroup_andRemapsCategories() {
        val remote = remoteSnapshot(categoryGroups = listOf(Envelope("acct-food", "Food", 0)))
        val local =
            docWith(
                listOf(LocalCategoryGroup("g-food", "  fOOD ", 0), leisure),
                "gym" to "g-food",
                "books" to "g-fun",
            )

        val plan = planMigration(local, remote)

        assertEquals(listOf("g-fun"), plan.categoryGroups.map { it.id })
        assertEquals("acct-food", plan.categories.single { it.id == "gym" }.groupId)
        assertEquals("g-fun", plan.categories.single { it.id == "books" }.groupId)
    }

    @Test
    fun localGroupIdAlreadyInAccount_isKeptAndNotWritten() {
        val remote = remoteSnapshot(categoryGroups = listOf(Envelope("g-food", "Meals", 0)))

        val plan = planMigration(docWith(listOf(food), "gym" to "g-food"), remote)

        assertTrue(plan.categoryGroups.isEmpty())
        assertEquals("g-food", plan.categories.single().groupId)
    }

    @Test
    fun twoLocalGroupsWithTheSameName_collapseIntoTheFirst() {
        val dup = LocalCategoryGroup("g-dup", "food", 1)

        val plan = planMigration(docWith(listOf(food, dup), "gym" to "g-dup"), remoteSnapshot())

        assertEquals(listOf("g-food"), plan.categoryGroups.map { it.id })
        assertEquals("g-food", plan.categories.single().groupId)
    }

    @Test
    fun unresolvedGroupIds_becomeNull() {
        val bad = LocalCategoryGroup("bad id!", "Broken", 0)

        val plan =
            planMigration(
                docWith(listOf(bad), "a" to "bad id!", "b" to "missing", "c" to null),
                remoteSnapshot(),
            )

        assertTrue(plan.categoryGroups.isEmpty())
        assertEquals(1, plan.skipped)
        assertEquals(listOf(null, null, null), plan.categories.sortedBy { it.id }.map { it.groupId })
    }

    @Test
    fun categoryReusedByName_isNotWritten() {
        val remote =
            remoteSnapshot(
                categories = listOf(userCategory("acct-gym", "Gym")),
                categoryGroups = listOf(Envelope("acct-food", "Food", 0)),
            )
        val local = localDoc(categories = listOf(localCategory("gym", "gym").copy(groupId = "g-food")))

        val plan = planMigration(local.copy(categoryGroups = listOf(food)), remote)

        assertTrue(plan.categories.isEmpty())
        assertTrue(plan.categoryGroups.isEmpty())
    }

    @Test
    fun builtInOverrideGroup_isRemapped() {
        val remote = remoteSnapshot(categoryGroups = listOf(Envelope("acct-food", "food", 0)))
        val override = localCategory("FOOD", name = null).copy(groupId = "g-food")
        val local = localDoc(categories = listOf(override)).copy(categoryGroups = listOf(food))

        val plan = planMigration(local, remote)

        assertEquals("acct-food", plan.categories.single().groupId)
    }

    @Test
    fun noGroups_planStaysEmpty() {
        assertNull(planMigration(localDoc(), remoteSnapshot()).categoryGroups.firstOrNull())
    }

    // ---- toBatches ----

    @Test
    fun groups_areBatchedFirst_withExpectedKeys() {
        val plan =
            planMigration(
                docWith(listOf(food), "gym" to "g-food"),
                remoteSnapshot(),
            )

        val batches = plan.toBatches()

        assertEquals(listOf("categoryGroups", "categories"), batches.map { it.first().collection })
        val group = batches.first().single()
        assertEquals("g-food", group.id)
        assertEquals("Food", group.data["name"])
        assertEquals(0, group.data["order"])
        assertEquals("g-food", batches[1].single().data["groupId"])
    }
}
