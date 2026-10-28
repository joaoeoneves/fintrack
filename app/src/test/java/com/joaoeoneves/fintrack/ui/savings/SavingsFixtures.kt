package com.joaoeoneves.fintrack.ui.savings

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.RecurringFrequency
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.repository.GoalRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import java.time.LocalDate

/** The fixed "today" of the savings tests. */
val SAVINGS_TODAY: LocalDate = LocalDate.of(2026, 10, 15)

@Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
fun savingsGoal(
    id: String = "g1",
    name: String = "Holiday",
    target: Long = 120_000L,
    type: GoalTargetType = GoalTargetType.ANNUAL,
    startDate: LocalDate = LocalDate.of(2026, 1, 1),
    targetDate: LocalDate? = null,
    initial: Long = 0L,
    recurringId: String? = null,
    archived: Boolean = false,
    order: Int = 0,
) = SavingsGoal(
    id = id,
    name = name,
    targetCents = target,
    targetType = type,
    startDate = startDate,
    targetDate = targetDate,
    initialCents = initial,
    archived = archived,
    order = order,
    recurringId = recurringId,
)

@Suppress("LongParameterList") // Test fixture: each test overrides only what it cares about.
fun savingsItem(
    id: String = "r1",
    amount: Long = 10_000L,
    day: Int = 5,
    category: CategoryId? = BuiltInCategories.INVESTMENTS.id,
    active: Boolean = true,
    frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    month: Int? = null,
    start: LocalDate = LocalDate.of(2026, 1, 1),
    end: LocalDate? = null,
    autoPost: Boolean = true,
) = RecurringItem(
    id = id,
    type = RecurringType.EXPENSE,
    name = "Holiday",
    amountCents = amount,
    categoryId = category,
    note = null,
    frequency = frequency,
    dayOfMonth = day,
    monthOfYear = month,
    startDate = start,
    endDate = end,
    handledThrough = null,
    active = active,
    autoPost = autoPost,
)

/** Records every write of both repositories, in call order, as `goal:save:<id>`, `item:delete:<id>`, ... */
class WriteLog {
    val entries = mutableListOf<String>()

    fun goals(delegate: GoalRepository): GoalRepository =
        object : GoalRepository by delegate {
            override suspend fun saveGoal(goal: SavingsGoal): Result<String> =
                delegate.saveGoal(goal).also { entries += "goal:save:${goal.id}" }

            override suspend fun deleteGoal(id: String): Result<Unit> {
                val result = delegate.deleteGoal(id)
                entries += "goal:delete:$id"
                return result
            }
        }

    fun recurring(delegate: RecurringRepository): RecurringRepository =
        object : RecurringRepository by delegate {
            override suspend fun saveRecurring(item: RecurringItem): Result<String> =
                delegate.saveRecurring(item).also { entries += "item:save:${item.id}" }

            override suspend fun deleteRecurring(id: String): Result<Unit> =
                delegate.deleteRecurring(id).also { entries += "item:delete:$id" }
        }
}
