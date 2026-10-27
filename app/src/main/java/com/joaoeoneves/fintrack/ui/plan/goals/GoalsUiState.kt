package com.joaoeoneves.fintrack.ui.plan.goals

import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.GoalProgress
import com.joaoeoneves.fintrack.domain.model.RecurringItem

/** What a goal can be linked to: the EXPENSE recurring items and the selectable categories. */
data class GoalEditorOptions(
    val recurring: List<RecurringItem> = emptyList(),
    val categories: List<Category> = emptyList(),
)

/**
 * The Plan tab's goals: [goals] are the active ones with their progress. [investmentCandidates] are the active
 * INVESTMENTS recurring items no goal is linked to yet (what "Create from investments" would turn into goals).
 * Nothing shows until [loaded].
 */
data class GoalsUiState(
    val loaded: Boolean = false,
    val goals: List<GoalProgress> = emptyList(),
    val options: GoalEditorOptions = GoalEditorOptions(),
    val investmentCandidates: List<RecurringItem> = emptyList(),
)

/** What the goals card and sheet can trigger, bundled for detekt's LongParameterList. */
data class GoalsActions(
    val onAdd: () -> Unit,
    val onEdit: (String) -> Unit,
    val onCreateFromInvestments: () -> Unit,
)
