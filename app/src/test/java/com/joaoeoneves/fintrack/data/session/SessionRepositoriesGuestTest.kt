package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.data.FirestoreBudgetRepository
import com.joaoeoneves.fintrack.data.FirestoreCategoryRepository
import com.joaoeoneves.fintrack.data.FirestoreExpenseRepository
import com.joaoeoneves.fintrack.data.FirestoreIncomeRepository
import com.joaoeoneves.fintrack.data.local.LocalBudgetRepository
import com.joaoeoneves.fintrack.data.local.LocalCategoryRepository
import com.joaoeoneves.fintrack.data.local.LocalExpenseRepository
import com.joaoeoneves.fintrack.data.local.LocalIncomeRepository
import com.joaoeoneves.fintrack.data.local.LocalStoreTestBase
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.usecase.CheckCategoryUsage
import dagger.Lazy
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The concrete session repositories as a guest: local only, the Firestore Lazy must never be resolved. */
class SessionRepositoriesGuestTest : LocalStoreTestBase() {
    private val sessions = FakeSessionRepository(initialSession = Session.Guest)
    private val store by lazy { newStore() }
    private val ids = SequenceIds("g")
    private val from = Instant.parse("2000-01-01T00:00:00Z")
    private val to = Instant.parse("2100-01-01T00:00:00Z")

    private fun <T : Any> forbidden(): Lazy<T> = Lazy { error("Firestore must not be resolved as a guest") }

    private val expenses by lazy {
        SessionExpenseRepository(sessions, forbidden<FirestoreExpenseRepository>(), LocalExpenseRepository(store, ids))
    }
    private val income by lazy {
        SessionIncomeRepository(sessions, forbidden<FirestoreIncomeRepository>(), LocalIncomeRepository(store, ids))
    }
    private val budgets by lazy {
        SessionBudgetRepository(sessions, forbidden<FirestoreBudgetRepository>(), LocalBudgetRepository(store))
    }
    private val categories by lazy {
        SessionCategoryRepository(
            sessions,
            forbidden<FirestoreCategoryRepository>(),
            LocalCategoryRepository(store, ids),
        )
    }

    @Test
    fun expenses_roundTripLocally() =
        runStoreTest {
            val id = expenses.addExpense(expense(id = "")).getOrThrow()
            assertEquals(listOf(id), expenses.observeExpenses(from, to).first().map { it.id })
            assertEquals(id, expenses.getExpense(id).getOrThrow()?.id)
            assertTrue(expenses.hasExpenseWithCategory(BuiltInCategories.FOOD.id).getOrThrow())
            assertTrue(expenses.deleteExpense(id).isSuccess)
            assertTrue(expenses.getAllExpenses().getOrThrow().isEmpty())
        }

    @Test
    fun income_roundTripLocally() =
        runStoreTest {
            val id = income.addIncome(income(id = "")).getOrThrow()
            assertEquals(listOf(id), income.observeIncome(from, to).first().map { it.id })
            assertEquals(1, income.getAllIncome().getOrThrow().size)
        }

    @Test
    fun budgets_roundTripLocally() =
        runStoreTest {
            assertTrue(budgets.setBudget(BuiltInCategories.FOOD.id, 5000).isSuccess)
            assertEquals(
                5000L,
                budgets
                    .observeBudgets()
                    .first()
                    .single()
                    .limitCents,
            )
            assertTrue(budgets.clearBudget(BuiltInCategories.FOOD.id).isSuccess)
            assertTrue(budgets.observeBudgets().first().isEmpty())
        }

    @Test
    fun categories_emptyStoreShowsBuiltIns_andCustomCategoryCanBeAdded() =
        runStoreTest {
            assertEquals(BuiltInCategories.all, categories.observeCategories().first())
            val id = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING).getOrThrow()
            assertTrue(categories.observeCategories().first().any { it.id == id })
        }

    @Test
    fun checkCategoryUsage_worksOffline_withLocalRepositories() =
        runStoreTest {
            val id = categories.addCategory("Gym", "home", "teal", CategoryKind.SPENDING).getOrThrow()
            val useCase = CheckCategoryUsage(expenses, budgets)

            assertFalse(useCase(id).getOrThrow().inUse)

            budgets.setBudget(id, 1000)
            assertEquals(true, useCase(id).getOrThrow().hasBudget)
            assertFalse(useCase(id).getOrThrow().hasExpenses)

            expenses.addExpense(expense(id = "", categoryId = id.value))
            assertTrue(useCase(id).getOrThrow().hasExpenses)
        }
}
