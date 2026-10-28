package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupNamesTest {
    private val groups = listOf(CategoryGroup("g1", "Food", 0), CategoryGroup("g2", "Fun", 1))

    @Test
    fun taken_matchesTrimmedCaseInsensitive() {
        assertTrue(isGroupNameTaken("  fOOd ", groups))
    }

    @Test
    fun notTaken_forNewName_orEmptyGroups() {
        assertFalse(isGroupNameTaken("Rent", groups))
        assertFalse(isGroupNameTaken("Food", emptyList()))
    }

    @Test
    fun excludingId_allowsOwnName_butNotOthers() {
        assertFalse(isGroupNameTaken("FOOD", groups, excludingId = "g1"))
        assertTrue(isGroupNameTaken("Fun", groups, excludingId = "g1"))
    }

    @Test
    fun reserved_counts_trimmedAndCaseInsensitive() {
        assertTrue(isGroupNameTaken("ungrouped", groups, reserved = listOf(" Ungrouped ")))
        assertFalse(isGroupNameTaken("Other", groups, reserved = listOf("Ungrouped")))
    }

    @Test
    fun suggestedGroupsFor_emptyCatalog_isEmpty() {
        assertTrue(suggestedGroupsFor(emptyList()).isEmpty())
    }

    @Test
    fun suggestedGroupsFor_keepsEnumOrder_andOnlyExistingIds() {
        val categories =
            listOf(BuiltInCategories.TRANSFER, BuiltInCategories.FOOD, BuiltInCategories.INVESTMENTS)
        val result = suggestedGroupsFor(categories)

        assertEquals(
            listOf(
                SuggestedGroup.INVESTMENTS to listOf(BuiltInCategories.INVESTMENTS.id),
                SuggestedGroup.FOOD to listOf(BuiltInCategories.FOOD.id),
                SuggestedGroup.OTHER to listOf(BuiltInCategories.TRANSFER.id),
            ),
            result,
        )
    }

    @Test
    fun suggestedGroupsFor_dropsArchived_andGroupLeftEmpty() {
        val categories = listOf(BuiltInCategories.FOOD.copy(archived = true), BuiltInCategories.SHOPPING)

        assertEquals(
            listOf(SuggestedGroup.OTHER to listOf(BuiltInCategories.SHOPPING.id)),
            suggestedGroupsFor(categories),
        )
    }

    @Test
    fun suggestedGroupsFor_builtInRecurring_isArchivedByDefault_soDropped() {
        assertTrue(suggestedGroupsFor(listOf(BuiltInCategories.RECURRING)).isEmpty())
    }

    @Test
    fun suggestedGroupsFor_ignoresCustomCategories() {
        assertTrue(suggestedGroupsFor(listOf(userCategory("gym"), userCategory("food"))).isEmpty())
    }
}
