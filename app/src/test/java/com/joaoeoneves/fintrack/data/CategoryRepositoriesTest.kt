package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.usecase.CategoryUsage
import com.joaoeoneves.fintrack.domain.usecase.CheckCategoryUsage
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.userCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** [FakeCategoryRepository], [CategoryResolvingExpenseRepository] and [CheckCategoryUsage]. */
class CategoryRepositoriesTest {
    private val date = Instant.parse("2026-10-10T12:00:00Z")
    private val range = Instant.parse("2026-10-01T00:00:00Z") to Instant.parse("2026-11-01T00:00:00Z")

    // ---- FakeCategoryRepository ----

    @Test
    fun fake_startsWithBuiltIns_addAppendsAfterHighestOrder() =
        runBlocking {
            val repo = FakeCategoryRepository()
            assertEquals(BuiltInCategories.all, repo.observeCategories().first())

            val id = repo.addCategory("Gym", "home", "teal", CategoryKind.SPENDING).getOrThrow()

            val added = repo.observeCategories().first().last()
            assertEquals(id, added.id)
            assertEquals("Gym", added.customName)
            assertEquals(5, added.order)
        }

    @Test
    fun fake_archiveReorderUpdateAndDelete() =
        runBlocking {
            val repo = FakeCategoryRepository(listOf(userCategory("gym", "Gym")))

            repo.setArchived(CategoryId("gym"), true)
            assertTrue(
                repo
                    .observeCategories()
                    .first()
                    .single { it.id.value == "gym" }
                    .archived,
            )

            repo.reorder(listOf(CategoryId("gym"), BuiltInCategories.FOOD.id))
            val catalog = repo.observeCategories().first()
            assertEquals(0, catalog.single { it.id.value == "gym" }.order)
            assertEquals(1, catalog.single { it.id == BuiltInCategories.FOOD.id }.order)

            repo.updateCategory(userCategory("gym", "Fitness"))
            assertEquals(
                "Fitness",
                repo
                    .observeCategories()
                    .first()
                    .single { it.id.value == "gym" }
                    .customName,
            )

            assertTrue(repo.deleteCategory(CategoryId("gym")).isSuccess)
            assertTrue(repo.observeCategories().first().none { it.id.value == "gym" })
        }

    @Test
    fun fake_deleteBuiltIn_failsWithIllegalArgument() =
        runBlocking {
            val result = FakeCategoryRepository().deleteCategory(BuiltInCategories.FOOD.id)

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }

    @Test
    fun fake_writeErrorKnob_failsOnce() =
        runBlocking {
            val repo = FakeCategoryRepository(nextWriteError = IllegalStateException("boom"))

            assertTrue(repo.setArchived(BuiltInCategories.FOOD.id, true).isFailure)
            assertTrue(repo.setArchived(BuiltInCategories.FOOD.id, true).isSuccess)
        }

    // ---- CategoryResolvingExpenseRepository ----

    @Test
    fun resolving_reEmitsWithRenamedCategory() =
        runBlocking {
            val gym = userCategory("gym", "Gym")
            val categories = FakeCategoryRepository(listOf(gym))
            val delegate = FakeExpenseRepository(listOf(testExpense("e", 100L, date, gym)))
            val repo = CategoryResolvingExpenseRepository(delegate, categories)

            val names = mutableListOf<String?>()
            val job =
                launch(Dispatchers.Unconfined) {
                    repo.observeExpenses(range.first, range.second).collect { names += it.single().category.customName }
                }
            categories.updateCategory(gym.copy(customName = "Fitness"))
            job.cancel()

            assertEquals(listOf<String?>("Gym", "Fitness"), names)
        }

    @Test
    fun resolving_getExpenseAndGetAll_resolve_missingBecomesUnknown() =
        runBlocking {
            val gym = userCategory("gym", "Gym")
            val stale = testExpense("a", 1L, date, gym.copy(customName = "Stale"))
            val orphan = testExpense("b", 1L, date, userCategory("deleted"))
            val repo =
                CategoryResolvingExpenseRepository(
                    FakeExpenseRepository(listOf(stale, orphan)),
                    FakeCategoryRepository(listOf(gym)),
                )

            assertEquals(
                "Gym",
                repo
                    .getExpense("a")
                    .getOrThrow()
                    ?.category
                    ?.customName,
            )
            assertTrue(
                repo
                    .getExpense("b")
                    .getOrThrow()!!
                    .category.isUnknown,
            )
            assertEquals(listOf("Gym"), repo.getAllExpenses().getOrThrow().mapNotNull { it.category.customName })
        }

    @Test
    fun resolving_writesAndUsageCheckDelegate() =
        runBlocking {
            val delegate = FakeExpenseRepository()
            val repo = CategoryResolvingExpenseRepository(delegate, FakeCategoryRepository())

            val id = repo.addExpense(testExpense("", 5L, date, BuiltInCategories.FOOD)).getOrThrow()

            assertEquals(1, delegate.getAllExpenses().getOrThrow().size)
            assertEquals(true, repo.hasExpenseWithCategory(BuiltInCategories.FOOD.id).getOrThrow())
            repo.deleteExpense(id)
            assertEquals(false, repo.hasExpenseWithCategory(BuiltInCategories.FOOD.id).getOrThrow())
        }

    // ---- CheckCategoryUsage ----

    private val gymId = CategoryId("gym")

    private fun usage(
        expenses: FakeExpenseRepository = FakeExpenseRepository(),
        budgets: FakeBudgetRepository = FakeBudgetRepository(),
        recurring: FakeRecurringRepository = FakeRecurringRepository(),
    ) = CheckCategoryUsage(expenses, budgets, recurring)

    @Test
    fun usage_unused() =
        runBlocking {
            assertEquals(CategoryUsage(false, false), usage()(gymId).getOrThrow())
            assertTrue(!usage()(gymId).getOrThrow().inUse)
        }

    @Test
    fun usage_expenseOnly_budgetOnly_both() =
        runBlocking {
            val expenses = FakeExpenseRepository(listOf(testExpense("e", 1L, date, userCategory("gym"))))
            val budgets = FakeBudgetRepository(listOf(Budget(gymId, 100L)))

            assertEquals(CategoryUsage(true, false), usage(expenses)(gymId).getOrThrow())
            assertEquals(CategoryUsage(false, true), usage(budgets = budgets)(gymId).getOrThrow())
            assertEquals(CategoryUsage(true, true), usage(expenses, budgets)(gymId).getOrThrow())
        }

    @Test
    fun usage_eitherFailure_isFailure() =
        runBlocking {
            val expenseError = IllegalStateException("offline")
            val budgetError = IllegalStateException("budgets down")

            val a = usage(FakeExpenseRepository(nextHasExpenseWithCategoryError = expenseError))(gymId)
            val b = usage(budgets = FakeBudgetRepository(nextObserveBudgetsError = budgetError))(gymId)

            assertEquals(expenseError, a.exceptionOrNull())
            assertEquals(budgetError, b.exceptionOrNull())
        }
}
