package com.joaoeoneves.fintrack.ui.dashboard

import android.app.Application
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.ExpenseCategory
import com.joaoeoneves.fintrack.domain.model.Income
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Shared fixtures/setup for [DashboardViewModel] tests. The original single test class grew past
 * detekt's LargeClass threshold, so it's split by feature area into [DashboardViewModelExpenseTest]
 * (loading/filtering/error-state for expenses), [DashboardViewModelBudgetTest] (budgets, over-budget,
 * onSetBudget, monthRange recompute), and [DashboardViewModelIncomeTest] (income/net-balance and its
 * own error-state handling) -- each of which extends this base for the common
 * Robolectric/coroutine-dispatcher/fixture-builder boilerplate.
 *
 * All expense/income fixtures are anchored relative to a real `Instant.now()` (as the ViewModel
 * itself uses `Instant.now()` internally to compute its query ranges) with margins comfortably
 * inside/outside each [com.joaoeoneves.fintrack.domain.model.TimeRange] bucket, to avoid boundary
 * flakiness.
 *
 * Budget-related fixtures additionally need to land inside/outside the *current calendar month*
 * (via [com.joaoeoneves.fintrack.domain.model.currentCalendarMonthRange], which -- like
 * [com.joaoeoneves.fintrack.domain.model.TimeRange.toInstantRange] -- is called with real defaults
 * inside the ViewModel and has no test seam to override "now"). Where a fixture would need to be both
 * "more than a week old" and "still within the current calendar month" -- which is impossible during
 * the first several days of a month -- the test uses `org.junit.Assume.assumeTrue` to skip itself
 * gracefully rather than risk flakiness.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(application = Application::class)
open class DashboardViewModelTestBase {
    protected val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected val now: Instant = Instant.now()
    protected val zone: ZoneId = ZoneId.systemDefault()
    protected val currentMonth: YearMonth = YearMonth.from(now.atZone(zone))

    // Named `name` after `id` (no test needs a distinct display name), keeping this under detekt's
    // LongParameterList threshold.
    protected fun expense(
        id: String,
        amountCents: Long,
        category: ExpenseCategory,
        date: Instant,
        note: String? = null,
    ) = Expense(id = id, name = id, amountCents = amountCents, category = category, date = date, note = note)

    protected fun income(
        id: String,
        source: String = id,
        amountCents: Long,
        date: Instant,
        note: String? = null,
    ) = Income(id = id, source = source, amountCents = amountCents, date = date, note = note)

    /** Noon on [day] of the given [yearMonth], safely away from midnight rollover edges. */
    protected fun noonOn(
        yearMonth: YearMonth,
        day: Int,
    ): Instant =
        yearMonth
            .atDay(day)
            .atTime(LocalTime.NOON)
            .atZone(zone)
            .toInstant()
}
