package com.joaoeoneves.fintrack.ui.dashboard

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.domain.model.StartStep
import com.joaoeoneves.fintrack.ui.dashboard.review.ReviewInboxModel
import com.joaoeoneves.fintrack.ui.dashboard.review.ReviewInboxSnackbars
import com.joaoeoneves.fintrack.ui.dashboard.review.ReviewInboxViewModel

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
)

/** Creates the view models behind [HomeBlocks], collects their state and hosts their snackbars. */
@Composable
fun rememberHomeBlocks(
    start: StartChecklistModel,
    callbacks: DashboardCallbacks,
    snackbarHostState: SnackbarHostState,
    reviewViewModel: ReviewInboxViewModel = hiltViewModel(),
): HomeBlocks {
    val reviewState by reviewViewModel.uiState.collectAsStateWithLifecycle()
    ReviewInboxSnackbars(reviewViewModel, snackbarHostState)
    return HomeBlocks(
        start = start,
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
