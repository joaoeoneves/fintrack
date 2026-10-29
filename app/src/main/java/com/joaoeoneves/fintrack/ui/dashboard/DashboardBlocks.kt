package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.QuickAddSuggestion
import com.joaoeoneves.fintrack.domain.model.StartStep
import com.joaoeoneves.fintrack.ui.dashboard.quickadd.QuickAddRow
import com.joaoeoneves.fintrack.ui.dashboard.quickadd.QuickAddViewModel
import com.joaoeoneves.fintrack.ui.dashboard.review.ReviewInboxCard
import com.joaoeoneves.fintrack.ui.dashboard.review.ReviewInboxModel
import com.joaoeoneves.fintrack.ui.dashboard.review.ReviewInboxSnackbars
import com.joaoeoneves.fintrack.ui.dashboard.review.ReviewInboxUiState
import com.joaoeoneves.fintrack.ui.dashboard.review.ReviewInboxViewModel
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.motionAnimateItem

/** The "Get started" card's state and callbacks. */
data class StartChecklistModel(
    val state: StartChecklistUiState,
    val onOpenStep: (StartStep) -> Unit,
    val onDismiss: () -> Unit,
)

/** The optional blocks of Home besides the summary: each hides itself when it has nothing to say. */
data class HomeBlocks(
    val start: StartChecklistModel,
    val review: ReviewInboxModel,
    val quickAdd: List<QuickAddSuggestion>,
)

/** Creates the view models behind [HomeBlocks], collects their state and hosts their snackbars. */
@Composable
fun rememberHomeBlocks(
    start: StartChecklistModel,
    callbacks: DashboardCallbacks,
    snackbarHostState: SnackbarHostState,
    reviewViewModel: ReviewInboxViewModel = hiltViewModel(),
    quickAddViewModel: QuickAddViewModel = hiltViewModel(),
): HomeBlocks {
    val reviewState by reviewViewModel.uiState.collectAsStateWithLifecycle()
    val quickAdd by quickAddViewModel.suggestions.collectAsStateWithLifecycle()
    ReviewInboxSnackbars(reviewViewModel, snackbarHostState)
    return HomeBlocks(
        start = start,
        quickAdd = quickAdd,
        review =
            ReviewInboxModel(
                state = reviewState,
                onConfirmAll = reviewViewModel::onConfirmAll,
                onRemove = reviewViewModel::onRemove,
                onOpenExpense = callbacks.onOpenExpense,
                onOpenIncome = callbacks.onOpenIncome,
            ),
    )
}

private val SectionSpacing = Dimens.SectionGap

/** A list item padded to the screen margin, with the standard reorder animation. */
@Composable
internal fun LazyItemScope.ScreenItem(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = motionAnimateItem(Modifier.padding(horizontal = Dimens.ScreenPadding).then(modifier)),
    ) { content() }
}

internal fun LazyListScope.startItems(start: StartChecklistModel) {
    if (start.state is StartChecklistUiState.Visible) {
        item(key = "start_checklist") {
            ScreenItem(Modifier.padding(top = Dimens.Space8)) {
                StartChecklistCard(state = start.state, onOpenStep = start.onOpenStep, onDismiss = start.onDismiss)
            }
        }
    }
}

internal fun LazyListScope.reviewItems(
    review: ReviewInboxModel,
    visible: Boolean,
) {
    if (visible && review.state is ReviewInboxUiState.Visible) {
        item(key = "review_inbox") {
            ScreenItem(Modifier.padding(top = SectionSpacing)) { ReviewInboxCard(state = review.state, model = review) }
        }
    }
}

/** The review, quick-add and recurring-suggestion blocks only make sense for the current month. */
internal val DashboardUiState.Content.showsCurrentMonthBlocks: Boolean
    get() = period !is Period.Year && budgetMonthIsCurrent

internal fun LazyListScope.quickAddItems(
    blocks: HomeBlocks,
    visible: Boolean,
    callbacks: DashboardCallbacks,
) {
    if (visible && blocks.quickAdd.isNotEmpty()) {
        item(key = "quick_add") {
            ScreenItem(Modifier.padding(top = SectionSpacing)) {
                QuickAddRow(suggestions = blocks.quickAdd, onQuickAdd = callbacks.onQuickAdd)
            }
        }
    }
}
