package com.joaoeoneves.fintrack.data.local

import android.app.Application
import com.joaoeoneves.fintrack.testutil.recurringItem
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every record id must have the shape `firestore.rules` demands: 1..64 characters of `[A-Za-z0-9_-]`. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RecordIdShapeTest {
    private val known = setOf("FOOD")
    private val badIds = listOf("a".repeat(65), "has space", "slash/id", "dot.id", "accént", "emoji😀", "")
    private val goodIds = listOf("a", "A-z_09", "a".repeat(64))

    private val expense = LocalExpense("e", "Lunch", 100, "FOOD", "2026-03-10T12:00:00Z")
    private val income = LocalIncome("i", "Pay", 100, "2026-03-10T12:00:00Z")
    private val recurring = recurringItem().toLocal()
    private val planned = LocalPlannedIncome("p", "Salary", 1000, 0)

    private fun checkAll(
        validate: (String) -> Unit,
        name: String,
    ) {
        goodIds.forEach { validate(it) }
        badIds.forEach { id ->
            assertThrows("$name accepted '$id'", IllegalArgumentException::class.java) { validate(id) }
        }
    }

    @Test
    fun expense() = checkAll({ validateExpense(expense.copy(id = it), known) }, "expense")

    @Test
    fun income() = checkAll({ validateIncome(income.copy(id = it)) }, "income")

    @Test
    fun recurring() = checkAll({ validateRecurring(recurring.copy(id = it), known) }, "recurring")

    @Test
    fun plannedIncome() = checkAll({ validatePlannedIncome(planned.copy(id = it)) }, "planned income")
}
