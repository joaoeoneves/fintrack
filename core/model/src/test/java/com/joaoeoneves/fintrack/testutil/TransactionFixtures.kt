package com.joaoeoneves.fintrack.testutil

import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import java.time.Instant

/** Concise [Expense] builder for domain tests; the display name mirrors [id]. */
fun testExpense(
    id: String,
    amountCents: Long,
    date: Instant,
    category: Category = BuiltInCategories.SHOPPING,
    isRefund: Boolean = false,
) = Expense(
    id = id,
    name = id,
    amountCents = amountCents,
    category = category,
    date = date,
    note = null,
    isRefund = isRefund,
)

/** Concise [Income] builder for domain tests; the source mirrors [id]. */
fun testIncome(
    id: String,
    amountCents: Long,
    date: Instant,
) = Income(id = id, source = id, amountCents = amountCents, date = date, note = null)
