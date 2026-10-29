package com.joaoeoneves.fintrack.ui.plan.envelopes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.common.LabeledIconButton
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.formatAmountCents
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.ListSkeleton
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.designsystem.StateCrossfade
import com.joaoeoneves.fintrack.ui.settings.categories.MoveDirection
import com.joaoeoneves.fintrack.ui.theme.Dimens

/** The envelopes list: tap a row to edit it, move rows with the arrows, a sticky "New envelope" button. */
@Composable
fun EnvelopesScreen(
    onBack: () -> Unit,
    onOpenEditor: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EnvelopesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }
    LargeTitleScaffold(
        title = stringResource(R.string.envelopes_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
        bottomBar = {
            PrimaryPillButton(
                text = stringResource(R.string.envelopes_new),
                onClick = { onOpenEditor(null) },
                modifier =
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space12),
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        StateCrossfade(state) { current ->
            when (current) {
                EnvelopesUiState.Loading -> ListSkeleton(contentModifier)

                is EnvelopesUiState.Error ->
                    EmptyState(
                        title = stringResource(R.string.error_state_title),
                        message = current.message.asString(),
                        modifier = contentModifier,
                    )

                is EnvelopesUiState.Content ->
                    EnvelopesList(current.rows, viewModel::onMove, onOpenEditor, contentModifier)
            }
        }
    }
}

@Composable
private fun EnvelopesList(
    rows: List<EnvelopeRowUi>,
    onMove: (String, MoveDirection) -> Unit,
    onOpenEditor: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
    ) {
        if (rows.isEmpty()) {
            item { EmptyState(title = stringResource(R.string.envelopes_empty)) }
        } else {
            item {
                SettingsGroup(label = stringResource(R.string.envelopes_title)) {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) Hairline()
                        EnvelopeRow(
                            row = row,
                            canMoveUp = index > 0,
                            canMoveDown = index < rows.lastIndex,
                            onMove = onMove,
                            onOpen = { onOpenEditor(row.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvelopeRow(
    row: EnvelopeRowUi,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (String, MoveDirection) -> Unit,
    onOpen: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingsRow(
            title = row.name,
            supportingText = row.statusText(),
            onClick = onOpen,
            modifier = Modifier.weight(1f),
        )
        LabeledIconButton(
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.category_move_up),
            onClick = { onMove(row.id, MoveDirection.UP) },
            enabled = canMoveUp,
        )
        LabeledIconButton(
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.category_move_down),
            onClick = { onMove(row.id, MoveDirection.DOWN) },
            enabled = canMoveDown,
        )
    }
}

@Composable
private fun EnvelopeRowUi.statusText(): String =
    when (val current = status) {
        EnvelopeRowStatus.Empty -> stringResource(R.string.envelope_row_empty)
        EnvelopeRowStatus.Tracking -> stringResource(R.string.envelope_row_tracking)
        is EnvelopeRowStatus.Budgeted ->
            stringResource(R.string.envelope_row_status, categoryCount, formatAmountCents(current.cents))
    }
