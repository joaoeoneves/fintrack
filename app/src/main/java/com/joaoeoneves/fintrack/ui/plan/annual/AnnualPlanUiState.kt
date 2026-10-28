package com.joaoeoneves.fintrack.ui.plan.annual

import androidx.annotation.StringRes
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AnnualPlan
import com.joaoeoneves.fintrack.ui.common.UiText

/** The two views of the Plan tab. */
enum class PlanTab(
    val token: String,
    @param:StringRes val labelRes: Int,
) {
    MONTH("month", R.string.plan_tab_month),
    YEAR("year", R.string.plan_tab_year),
    ;

    companion object {
        /** The tab stored under [token]; anything unknown is the month view. */
        fun fromToken(token: String?): PlanTab = entries.firstOrNull { it.token == token } ?: MONTH
    }
}

/** State of the Year view. */
sealed interface AnnualPlanUiState {
    data object Loading : AnnualPlanUiState

    data class Error(
        val message: UiText,
    ) : AnnualPlanUiState

    /** [plan] is the evaluated [year]; [canGoPrevious]/[canGoNext] bound the year navigator. */
    data class Content(
        val year: Int,
        val plan: AnnualPlan,
        val canGoPrevious: Boolean,
        val canGoNext: Boolean,
    ) : AnnualPlanUiState {
        /** Whether anything at all is planned or spent, i.e. whether the year view has something to show. */
        val hasData: Boolean
            get() =
                plan.income.totalCents > 0 ||
                    plan.plannedCents > 0 ||
                    plan.envelopes.any { it.actualCents > 0 } ||
                    plan.subscriptions.isNotEmpty()
    }
}

/** What the Year view can trigger, bundled for detekt's LongParameterList. */
data class AnnualPlanActions(
    val onPreviousYear: () -> Unit,
    val onNextYear: () -> Unit,
    val onRetry: () -> Unit,
)
