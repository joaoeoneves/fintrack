package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreExpenseRepository
import com.joaoeoneves.fintrack.data.local.LocalExpenseRepository
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.repository.BulkAddResult
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** [ExpenseRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
@Suppress("TooManyFunctions") // One override per ExpenseRepository operation.
class SessionExpenseRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestoreExpenseRepository>,
        local: LocalExpenseRepository,
    ) : ExpenseRepository {
        private val switch = SessionSwitch<ExpenseRepository>(sessionRepository, remote, local)

        override fun observeExpenses(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<Expense>> = switch.flow(emptyList()) { it.observeExpenses(startInclusive, endExclusive) }

        override fun observeHasAnyExpense(): Flow<Boolean> = switch.flow(false) { it.observeHasAnyExpense() }

        private val needingReview: Flow<List<Expense>> = switch.flow(emptyList()) { it.observeNeedingReview() }

        override fun observeNeedingReview(): Flow<List<Expense>> = needingReview

        override suspend fun markReviewed(ids: Collection<String>): Result<Unit> = switch.call { it.markReviewed(ids) }

        override suspend fun addExpense(expense: Expense): Result<String> = switch.call { it.addExpense(expense) }

        override suspend fun updateExpense(expense: Expense): Result<Unit> = switch.call { it.updateExpense(expense) }

        override suspend fun deleteExpense(id: String): Result<Unit> = switch.call { it.deleteExpense(id) }

        override suspend fun addExpensesKeepingIds(expenses: List<Expense>): Result<List<String>> =
            switch.call { it.addExpensesKeepingIds(expenses) }

        override suspend fun updateExpenses(expenses: List<Expense>): Result<Unit> =
            switch.call {
                it.updateExpenses(expenses)
            }

        override suspend fun deleteExpenses(ids: Collection<String>): Result<Unit> =
            switch.call {
                it.deleteExpenses(ids)
            }

        private val linkedRefunds: Flow<List<Expense>> = switch.flow(emptyList()) { it.observeLinkedRefunds() }

        override fun observeLinkedRefunds(): Flow<List<Expense>> = linkedRefunds

        override suspend fun getExpense(id: String): Result<Expense?> = switch.call { it.getExpense(id) }

        override suspend fun getAllExpenses(): Result<List<Expense>> = switch.call { it.getAllExpenses() }

        override suspend fun addExpenses(expenses: List<Expense>): BulkAddResult =
            switch.call {
                it.addExpenses(expenses)
            }

        override suspend fun hasExpenseWithCategory(categoryId: CategoryId): Result<Boolean> =
            switch.call { it.hasExpenseWithCategory(categoryId) }

        override suspend fun expenseIdsBetween(
            firstId: String,
            lastId: String,
        ): Result<Set<String>> = switch.call { it.expenseIdsBetween(firstId, lastId) }

        override suspend fun countExpensesWithCategory(categoryId: CategoryId): Result<Int> =
            switch.call { it.countExpensesWithCategory(categoryId) }

        override suspend fun moveExpensesToCategory(
            source: CategoryId,
            target: CategoryId,
        ): Result<Int> = switch.call { it.moveExpensesToCategory(source, target) }
    }
