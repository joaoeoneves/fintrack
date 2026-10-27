package com.joaoeoneves.fintrack.data.migration

import com.joaoeoneves.fintrack.data.local.LocalSavingsGoal
import com.joaoeoneves.fintrack.data.local.validateGoal

/**
 * Valid goals whose id is not in [existingIds] yet, the linked category remapped through [categoryTarget] to the
 * account's id (dropped when it cannot be resolved; the goal stays).
 */
internal fun goalsToCopy(
    local: List<LocalSavingsGoal>,
    existingIds: Set<String>,
    categoryTarget: (String) -> String?,
): List<LocalSavingsGoal> =
    local
        .filter { it.id !in existingIds }
        .map { goal -> goal.copy(linkedCategoryId = goal.linkedCategoryId?.let(categoryTarget)) }
        .filter { runCatching { validateGoal(it) }.isSuccess }
