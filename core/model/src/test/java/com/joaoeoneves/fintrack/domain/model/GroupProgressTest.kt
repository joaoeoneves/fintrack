package com.joaoeoneves.fintrack.domain.model

import com.joaoeoneves.fintrack.testutil.recurringItem
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

/** [groupProgressOf] and the spreadsheet-shaped scenario the groups feature was built for. */
class GroupProgressTest {
    private val october = YearMonth.of(2026, 10)
    private val august = YearMonth.of(2026, 8)
    private val noon = Instant.parse("2026-10-10T12:00:00Z")

    private val recurringGroup = CategoryGroup("g-rec", "Recurring", 0)
    private val investmentsGroup = CategoryGroup("g-inv", "Investments", 1)
    private val foodGroup = CategoryGroup("g-food", "Food", 2)

    private val snacks = userCategory("snacks", "Snacks", order = 20).copy(groupId = foodGroup.id)
    private val catalog =
        listOf(
            BuiltInCategories.RECURRING.copy(groupId = recurringGroup.id),
            BuiltInCategories.INVESTMENTS.copy(groupId = investmentsGroup.id),
            BuiltInCategories.FOOD.copy(groupId = foodGroup.id),
            snacks,
            BuiltInCategories.SHOPPING,
            BuiltInCategories.TRANSFER,
        )
    private val groups = listOf(foodGroup, recurringGroup, investmentsGroup)

    private fun row(
        category: Category,
        limit: Long? = null,
        spent: Long = 0L,
    ) = CategoryBudget(category, limit, spent)

    @Test
    fun noData_listsEmptyGroupsInOrder_withoutOther() {
        val result = groupProgressOf(emptyList(), catalog, groups)

        assertEquals(listOf(recurringGroup, investmentsGroup, foodGroup), result.map { it.group })
        assertTrue(result.all { it.planned == 0L && it.spent == 0L && it.categories.isEmpty() })
    }

    @Test
    fun noGroupsAndNoData_isEmpty() {
        assertTrue(groupProgressOf(emptyList(), catalog, emptyList()).isEmpty())
    }

    @Test
    fun orderTies_breakById() {
        val a = CategoryGroup("a", "A", 1)
        val b = CategoryGroup("b", "B", 1)

        assertEquals(listOf(a, b), groupProgressOf(emptyList(), catalog, listOf(b, a)).map { it.group })
    }

    @Test
    fun ungroupedCategoryWithData_appearsAsImplicitOtherLast() {
        val rows = listOf(row(BuiltInCategories.FOOD, 1_000L, 400L), row(BuiltInCategories.SHOPPING, null, 700L))

        val result = groupProgressOf(rows, catalog, groups)

        val other = result.last()
        assertNull(other.group)
        assertEquals(0L, other.planned)
        assertEquals(700L, other.spent)
        assertEquals(foodGroup, result[2].group)
        assertEquals(1_000L, result[2].planned)
    }

    @Test
    fun ungroupedCategoryWithoutData_doesNotCreateOther() {
        val rows = listOf(row(BuiltInCategories.SHOPPING), row(BuiltInCategories.FOOD, 100L))

        val result = groupProgressOf(rows, catalog, groups)

        assertTrue(result.none { it.group == null })
    }

    @Test
    fun danglingGroupId_countsAsOther() {
        val orphan = userCategory("orphan", order = 30).copy(groupId = "deleted")

        val result = groupProgressOf(listOf(row(orphan, 500L, 100L)), catalog + orphan, groups)

        assertNull(result.last().group)
        assertEquals(500L, result.last().planned)
    }

    @Test
    fun categoryMissingFromCatalog_countsAsOther() {
        val result = groupProgressOf(listOf(row(userCategory("ghost"), null, 50L)), catalog, groups)

        assertNull(result.last().group)
        assertEquals(50L, result.last().spent)
    }

    @Test
    fun archivedCategoryWithSpend_isStillCounted() {
        val archived = BuiltInCategories.RECURRING
        assertTrue(archived.archived)

        val result = groupProgressOf(listOf(row(archived, null, 8_650L)), catalog, groups)

        val group = result.single { it.group == recurringGroup }
        assertEquals(8_650L, group.spent)
        assertEquals(listOf(archived), group.categories.map { it.category })
    }

    @Test
    fun isOver_onlyWhenPlannedPositiveAndSpendExceedsIt() {
        val over = GroupProgress(foodGroup, 100L, 101L, emptyList())

        assertTrue(over.isOver)
        assertFalse(GroupProgress(foodGroup, 100L, 100L, emptyList()).isOver)
        assertFalse(GroupProgress(foodGroup, 0L, 50L, emptyList()).isOver)
    }

    @Test
    fun hasRecurringPlanned_reflectsRowSources() {
        val recurringRow = CategoryBudget(snacks, null, 0L, 1_500L, PlannedSource.RECURRING)

        assertTrue(GroupProgress(foodGroup, 1_500L, 0L, listOf(recurringRow)).hasRecurringPlanned)
        assertFalse(GroupProgress(foodGroup, 100L, 0L, listOf(row(snacks, 100L))).hasRecurringPlanned)
    }

    // ---- spreadsheet fixture ----

    private val recurringItems =
        listOf(
            recurringItem("r1", amountCents = 5_000L, categoryId = BuiltInCategories.RECURRING.id),
            recurringItem("r2", amountCents = 2_000L, categoryId = BuiltInCategories.RECURRING.id),
            recurringItem("r3", amountCents = 1_000L, categoryId = BuiltInCategories.RECURRING.id),
            recurringItem("r4", amountCents = 650L, categoryId = BuiltInCategories.RECURRING.id),
        )
    private val budgets =
        listOf(
            Budget(BuiltInCategories.INVESTMENTS.id, 100_000L),
            Budget(BuiltInCategories.FOOD.id, 16_000L),
            Budget(snacks.id, 1_500L, skipMonths = setOf(8)),
        )

    private fun spreadsheet(month: YearMonth) =
        groupProgressOf(
            budgetProgressOf(emptyList(), budgets, catalog, recurringItems, month),
            catalog,
            groups,
        ).associateBy { it.group?.id }

    @Test
    fun spreadsheet_october() {
        val byGroup = spreadsheet(october)

        assertEquals(8_650L, byGroup.getValue("g-rec").planned)
        assertTrue(byGroup.getValue("g-rec").hasRecurringPlanned)
        assertEquals(100_000L, byGroup.getValue("g-inv").planned)
        assertFalse(byGroup.getValue("g-inv").hasRecurringPlanned)
        assertEquals(17_500L, byGroup.getValue("g-food").planned)
        assertFalse(byGroup.containsKey(null))
    }

    @Test
    fun spreadsheet_august_skipsSnacks() {
        val byGroup = spreadsheet(august)

        assertEquals(16_000L, byGroup.getValue("g-food").planned)
        assertEquals(8_650L, byGroup.getValue("g-rec").planned)
        assertEquals(100_000L, byGroup.getValue("g-inv").planned)
    }

    @Test
    fun spreadsheet_spendIsSummedPerGroup_andOverFlagged() {
        val expenses =
            listOf(
                testExpense("e1", 15_000L, noon, BuiltInCategories.FOOD),
                testExpense("e2", 3_000L, noon, snacks),
            )

        val rows = budgetProgressOf(expenses, budgets, catalog, recurringItems, october)
        val food = groupProgressOf(rows, catalog, groups).single { it.group == foodGroup }

        assertEquals(18_000L, food.spent)
        assertEquals(17_500L, food.planned)
        assertTrue(food.isOver)
    }
}
