package com.joaoeoneves.fintrack.domain.model

import java.time.Instant

/**
 * A new, unsaved copy of this expense dated [today]: name, amount, category, note, refund flag and meal card are kept;
 * the id, links (refundOf, splitId) and the review flag are not.
 */
fun Expense.duplicated(today: Instant): Expense =
    copy(id = "", date = today, refundOf = null, splitId = null, needsReview = false)
