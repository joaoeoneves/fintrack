package com.joaoeoneves.fintrack.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.usecase.DeletedTransaction
import com.joaoeoneves.fintrack.ui.common.LocalDeleteUndo
import com.joaoeoneves.fintrack.ui.common.TransactionUndoViewModel
import com.joaoeoneves.fintrack.ui.common.rememberDeletedMessage
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackNavigationBar
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.LocalAppSnackbar
import com.joaoeoneves.fintrack.ui.designsystem.NavigationBarEntry
import com.joaoeoneves.fintrack.ui.designsystem.showActionSnackbar
import com.joaoeoneves.fintrack.ui.plan.envelopes.DeletedEnvelope
import com.joaoeoneves.fintrack.ui.plan.envelopes.EnvelopeUndoViewModel
import com.joaoeoneves.fintrack.ui.plan.envelopes.LocalEnvelopeDeleteUndo
import com.joaoeoneves.fintrack.ui.recurring.LocalRecurringDeleteUndo
import com.joaoeoneves.fintrack.ui.recurring.RecurringUndoViewModel
import com.joaoeoneves.fintrack.ui.savings.DeletedSavingsPlan
import com.joaoeoneves.fintrack.ui.savings.LocalSavingsPlanDeleteUndo
import com.joaoeoneves.fintrack.ui.savings.SavingsUndoViewModel
import com.joaoeoneves.fintrack.ui.theme.LocalReducedMotion
import com.joaoeoneves.fintrack.ui.theme.Motion
import kotlinx.coroutines.launch

/**
 * Hosts the nav graph under a bottom [FinTrackNavigationBar] that is shown on the tab roots
 * only (forms, Settings and other pushed screens hide it).
 */
@Composable
fun FinTrackAppShell(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val destination = navController.currentBackStackEntryAsState().value?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit =
        remember { { message -> scope.launch { snackbarHostState.showSnackbar(message) } } }
    val undoViewModel = hiltViewModel<TransactionUndoViewModel>()
    val deletedMessage = rememberDeletedMessage()
    val undoLabel = stringResource(R.string.action_undo)
    val showDeleted: (DeletedTransaction) -> Unit =
        remember(deletedMessage, undoLabel) {
            { deleted ->
                scope.launch {
                    val result = snackbarHostState.showActionSnackbar(deletedMessage(deleted), undoLabel)
                    if (result == SnackbarResult.ActionPerformed) undoViewModel.restore(deleted)
                }
            }
        }
    val envelopeUndoViewModel = hiltViewModel<EnvelopeUndoViewModel>()
    val envelopeDeletedMessage = stringResource(R.string.envelope_deleted)
    val showEnvelopeDeleted: (DeletedEnvelope) -> Unit =
        remember(envelopeDeletedMessage, undoLabel) {
            { deleted ->
                scope.launch {
                    val result = snackbarHostState.showActionSnackbar(envelopeDeletedMessage, undoLabel)
                    if (result == SnackbarResult.ActionPerformed) envelopeUndoViewModel.restore(deleted)
                }
            }
        }
    val showPlanDeleted = rememberSavingsPlanDeletedHandler(snackbarHostState, undoLabel)
    val showRecurringDeleted = rememberRecurringDeletedHandler(snackbarHostState, undoLabel)
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        // Each screen inside applies its own system-bar insets; the bar below applies the bottom one.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
        bottomBar = { AnimatedTabBar(destination, navController::navigateToTab) },
    ) { inner ->
        CompositionLocalProvider(
            LocalAppSnackbar provides showMessage,
            LocalDeleteUndo provides showDeleted,
            LocalEnvelopeDeleteUndo provides showEnvelopeDeleted,
            LocalSavingsPlanDeleteUndo provides showPlanDeleted,
            LocalRecurringDeleteUndo provides showRecurringDeleted,
        ) {
            FinTrackNavHost(
                navController = navController,
                modifier = Modifier.padding(inner).consumeWindowInsets(inner),
            )
        }
    }
}

@Composable
private fun AnimatedTabBar(
    destination: NavDestination?,
    onSelect: (TopLevelTab) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = destination.isTopLevel(),
        enter = barEnter(reduced),
        exit = barExit(reduced),
    ) {
        TabBar(selected = destination.currentTab(), onSelect = onSelect)
    }
}

/** The bar slides up while fading in; under reduced motion it is a short fade. */
private fun barEnter(reduced: Boolean): EnterTransition =
    if (reduced) {
        fadeIn(tween(Motion.Short))
    } else {
        slideInVertically(tween(Motion.Medium, easing = Motion.EmphasizedDecelerate)) { it } +
            fadeIn(tween(Motion.Medium))
    }

private fun barExit(reduced: Boolean): ExitTransition =
    if (reduced) {
        fadeOut(tween(Motion.Short))
    } else {
        slideOutVertically(tween(Motion.Medium, easing = Motion.EmphasizedAccelerate)) { it } +
            fadeOut(tween(Motion.Medium))
    }

@Composable
private fun TabBar(
    selected: TopLevelTab?,
    onSelect: (TopLevelTab) -> Unit,
) {
    val entries =
        TopLevelTab.entries.map { tab ->
            NavigationBarEntry(label = stringResource(tab.labelRes), icon = tab.icon, testTag = tab.testTag)
        }
    FinTrackNavigationBar(
        entries = entries,
        selectedIndex = selected?.ordinal,
        onSelect = { onSelect(TopLevelTab.entries[it]) },
    )
}

@Composable
private fun rememberSavingsPlanDeletedHandler(
    snackbarHostState: SnackbarHostState,
    undoLabel: String,
): (DeletedSavingsPlan) -> Unit {
    val scope = rememberCoroutineScope()
    val undoViewModel = hiltViewModel<SavingsUndoViewModel>()
    val message = stringResource(R.string.savings_plan_deleted)
    return remember(message, undoLabel) {
        { deleted ->
            scope.launch {
                val result = snackbarHostState.showActionSnackbar(message, undoLabel)
                if (result == SnackbarResult.ActionPerformed) undoViewModel.restore(deleted)
            }
        }
    }
}

@Composable
private fun rememberRecurringDeletedHandler(
    snackbarHostState: SnackbarHostState,
    undoLabel: String,
): (RecurringItem) -> Unit {
    val scope = rememberCoroutineScope()
    val undoViewModel = hiltViewModel<RecurringUndoViewModel>()
    val message = stringResource(R.string.recurring_deleted)
    return remember(message, undoLabel) {
        { deleted ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showActionSnackbar(message, undoLabel)
                if (result == SnackbarResult.ActionPerformed) undoViewModel.restore(deleted)
            }
        }
    }
}
