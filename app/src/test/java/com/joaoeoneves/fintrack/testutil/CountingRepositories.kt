package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.data.FakeBudgetRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.InstantRange
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.time.Instant

/**
 * Tracks listener usage of a wrapped flow: every `observe*` call is recorded in [observedRanges]
 * and [active] counts the currently collected flows (incremented on start, decremented on
 * completion/cancellation), standing in for live Firestore snapshot listeners.
 */
class ListenerCounter {
    val observedRanges = mutableListOf<InstantRange>()
    var active = 0
        private set

    fun <T> track(
        range: InstantRange,
        flow: Flow<T>,
    ): Flow<T> {
        observedRanges += range
        return flow.onStart { active++ }.onCompletion { active-- }
    }
}

/** [ExpenseRepository] decorator counting `observeExpenses` listeners; writes go to [delegate]. */
class CountingExpenseRepository(
    val delegate: FakeExpenseRepository = FakeExpenseRepository(),
) : ExpenseRepository by delegate {
    constructor(expenses: List<Expense>) : this(FakeExpenseRepository(expenses))

    val listeners = ListenerCounter()

    override fun observeExpenses(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<Expense>> =
        listeners.track(
            InstantRange(startInclusive, endExclusive),
            delegate.observeExpenses(startInclusive, endExclusive),
        )
}

/** [IncomeRepository] decorator counting `observeIncome` listeners; writes go to [delegate]. */
class CountingIncomeRepository(
    val delegate: FakeIncomeRepository = FakeIncomeRepository(),
) : IncomeRepository by delegate {
    constructor(income: List<Income>) : this(FakeIncomeRepository(income))

    val listeners = ListenerCounter()

    override fun observeIncome(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<Income>> =
        listeners.track(
            InstantRange(startInclusive, endExclusive),
            delegate.observeIncome(startInclusive, endExclusive),
        )
}

/** [BudgetRepository] decorator counting `observeBudgets` listeners; writes go to [delegate]. */
class CountingBudgetRepository(
    val delegate: FakeBudgetRepository = FakeBudgetRepository(),
) : BudgetRepository by delegate {
    constructor(budgets: List<Budget>) : this(FakeBudgetRepository(budgets))

    val listeners = ListenerCounter()

    override fun observeBudgets(): Flow<List<Budget>> = listeners.track(ALL_TIME, delegate.observeBudgets())

    private companion object {
        // Budgets aren't range-scoped; a placeholder range keeps [ListenerCounter] uniform.
        val ALL_TIME = InstantRange(Instant.MIN, Instant.MAX)
    }
}
