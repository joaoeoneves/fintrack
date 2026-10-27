package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeSessionRepository
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import dagger.Lazy
import java.time.Instant

/** Counts how often the lazily provided remote was resolved (a guest must never resolve it). */
class CountingLazy<T : Any>(
    private val value: T?,
) : Lazy<T> {
    var gets = 0
        private set

    override fun get(): T {
        gets++
        return checkNotNull(value) { "remote must not be resolved" }
    }
}

abstract class SessionSwitchTestBase {
    protected val from: Instant = Instant.parse("2000-01-01T00:00:00Z")
    protected val to: Instant = Instant.parse("2100-01-01T00:00:00Z")

    protected fun user(uid: String) = AuthUser(uid = uid, displayName = null, email = null, photoUrl = null)

    protected fun expense(name: String) =
        Expense(
            id = name,
            name = name,
            amountCents = 100,
            category = BuiltInCategories.SHOPPING,
            date = Instant.parse("2026-01-01T00:00:00Z"),
            note = null,
        )

    protected val remoteRepo = FakeExpenseRepository(listOf(expense("remote")))
    protected val localRepo = FakeExpenseRepository(listOf(expense("local")))
    protected val remote = CountingLazy<ExpenseRepository>(remoteRepo)

    protected fun switchFor(session: Session): Pair<SessionSwitch<ExpenseRepository>, FakeSessionRepository> {
        val sessions = FakeSessionRepository(initialSession = session)
        return SessionSwitch<ExpenseRepository>(sessions, remote, localRepo) to sessions
    }

    protected fun SessionSwitch<ExpenseRepository>.observe() = flow(emptyList()) { it.observeExpenses(from, to) }
}
