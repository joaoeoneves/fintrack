package com.joaoeoneves.fintrack.domain.model

import java.time.Instant

data class Expense(
    val id: String,
    val name: String,
    val amountCents: Long,
    val category: ExpenseCategory,
    val date: Instant,
    val note: String?,
)
