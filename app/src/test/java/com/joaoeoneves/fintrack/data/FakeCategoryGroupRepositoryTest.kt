package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryGroup
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The group-related behavior of [FakeCategoryGroupRepository], [FakeCategoryRepository] and [FakeBudgetRepository]. */
class FakeCategoryGroupRepositoryTest {
    @Test
    fun add_rename_reorder_roundTrip() =
        runBlocking {
            val repo = FakeCategoryGroupRepository()
            val a = repo.addGroup("A").getOrThrow()
            val b = repo.addGroup("B").getOrThrow()

            repo.renameGroup(a, "Alpha")
            repo.reorderGroups(listOf(b, a))

            assertEquals(
                listOf(CategoryGroup(b, "B", 0), CategoryGroup(a, "Alpha", 1)),
                repo.observeGroups().first(),
            )
        }

    @Test
    fun validation_rejectsEmptyAndTooLongNames_andUnknownIds() =
        runBlocking {
            val repo = FakeCategoryGroupRepository()
            val id = repo.addGroup("A").getOrThrow()

            assertTrue(repo.addGroup("").isFailure)
            assertTrue(repo.addGroup("x".repeat(MAX_GROUP_NAME_LENGTH + 1)).isFailure)
            assertTrue(repo.renameGroup("nope", "X").isFailure)
            assertTrue(repo.renameGroup(id, "").isFailure)
            assertTrue(repo.reorderGroups(listOf("nope")).isFailure)
            assertEquals(listOf("A"), repo.groups.map { it.name })
        }

    @Test
    fun delete_ungroupsLinkedCategories() =
        runBlocking {
            val categories = FakeCategoryRepository()
            val repo = FakeCategoryGroupRepository(categories = categories)
            val g = repo.addGroup("G").getOrThrow()
            val kept = repo.addGroup("Kept").getOrThrow()
            val custom = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING, g).getOrThrow()
            categories.setGroup(BuiltInCategories.FOOD.id, g)
            categories.setGroup(BuiltInCategories.SHOPPING.id, kept)

            assertTrue(repo.deleteGroup(g).isSuccess)

            val catalog = categories.observeCategories().first().associateBy { it.id }
            assertNull(catalog.getValue(custom).groupId)
            assertNull(catalog.getValue(BuiltInCategories.FOOD.id).groupId)
            assertEquals(kept, catalog.getValue(BuiltInCategories.SHOPPING.id).groupId)
            assertEquals(listOf(kept), repo.groups.map { it.id })
        }

    @Test
    fun writeError_failsOnce_thenRecovers() =
        runBlocking {
            val boom = IllegalStateException("boom")
            val repo = FakeCategoryGroupRepository(nextWriteError = boom)

            assertEquals(boom, repo.addGroup("A").exceptionOrNull())
            assertTrue(repo.addGroup("A").isSuccess)
        }

    @Test
    fun categoryFake_setGroup_unknownCategoryFails_andAddCategoryKeepsGroup() =
        runBlocking {
            val categories = FakeCategoryRepository()

            assertTrue(categories.setGroup(CategoryId("ghost"), "g").isFailure)
            val id = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING, "g").getOrThrow()
            assertEquals(
                "g",
                categories
                    .observeCategories()
                    .first()
                    .single { it.id == id }
                    .groupId,
            )
        }

    @Test
    fun budgetFake_setBudget_replacesSkipMonths() =
        runBlocking {
            val budgets = FakeBudgetRepository()
            val food = BuiltInCategories.FOOD.id

            budgets.setBudget(food, 100, setOf(8))
            assertEquals(
                setOf(8),
                budgets
                    .observeBudgets()
                    .first()
                    .single()
                    .skipMonths,
            )

            budgets.setBudget(food, 200)
            val budget = budgets.observeBudgets().first().single()
            assertEquals(200L, budget.limitCents)
            assertEquals(emptySet<Int>(), budget.skipMonths)
        }
}
