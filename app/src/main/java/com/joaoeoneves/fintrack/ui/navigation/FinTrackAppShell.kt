package com.joaoeoneves.fintrack.ui.navigation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.Transaction
import com.joaoeoneves.fintrack.ui.common.LocalDeleteUndo
import com.joaoeoneves.fintrack.ui.common.TransactionUndoViewModel
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackNavigationBar
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.LocalAppSnackbar
import com.joaoeoneves.fintrack.ui.designsystem.NavigationBarEntry
import kotlinx.coroutines.launch

/**
 * Hosts the nav graph under a bottom [FinTrackNavigationBar] that is shown on the four tab roots
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
    val deletedTemplate = stringResource(R.string.activity_deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)
    val locale = currentLocale()
    val showDeleted: (Transaction) -> Unit =
        remember(deletedTemplate, undoLabel, locale) {
            { transaction ->
                scope.launch {
                    val message = String.format(locale, deletedTemplate, transaction.title())
                    val result = snackbarHostState.showSnackbar(message, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) undoViewModel.restore(transaction)
                }
            }
        }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        // Each screen inside applies its own system-bar insets; the bar below applies the bottom one.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(visible = destination.isTopLevel()) {
                TabBar(selected = destination.currentTab(), onSelect = navController::navigateToTab)
            }
        },
    ) { inner ->
        CompositionLocalProvider(LocalAppSnackbar provides showMessage, LocalDeleteUndo provides showDeleted) {
            FinTrackNavHost(
                navController = navController,
                modifier = Modifier.padding(inner).consumeWindowInsets(inner),
            )
        }
    }
}

private fun Transaction.title(): String =
    when (this) {
        is Expense -> name
        is Income -> source
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
