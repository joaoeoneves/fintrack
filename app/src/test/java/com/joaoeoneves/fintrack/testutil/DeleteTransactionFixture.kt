package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.domain.usecase.DeleteTransaction

/** A [DeleteTransaction] over the given repositories (fresh in-memory fakes for the ones not passed). */
fun deleteTransactionFor(
    expenses: ExpenseRepository = FakeExpenseRepository(),
    incomes: IncomeRepository = FakeIncomeRepository(),
    recurring: RecurringRepository = FakeRecurringRepository(),
) = DeleteTransaction(expenses, incomes, recurring)
