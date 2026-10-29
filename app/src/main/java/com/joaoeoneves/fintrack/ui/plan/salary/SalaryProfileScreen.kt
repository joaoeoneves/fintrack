package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.SalaryInputMode
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.ui.common.currentLocale
import com.joaoeoneves.fintrack.ui.common.moneySymbolsFor
import com.joaoeoneves.fintrack.ui.designsystem.CardsSkeleton
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.DialogTextButton
import com.joaoeoneves.fintrack.ui.designsystem.EditorScaffold
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.designsystem.StateCrossfade
import com.joaoeoneves.fintrack.ui.designsystem.rememberIsDirty
import com.joaoeoneves.fintrack.ui.designsystem.showActionSnackbar
import com.joaoeoneves.fintrack.ui.theme.Dimens

/**
 * The salary profile: the user's gross (with rates) or net amounts, from which the plan's income lines are
 * generated. Every field is optional except the base salary (gross mode) or the net salary (net mode).
 */
@Composable
fun SalaryProfileScreen(
    onBack: () -> Unit,
    viewModel: SalaryProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val duplicates by viewModel.duplicates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val separator = moneySymbolsFor(currentLocale()).decimal
    LaunchedEffect(separator) { viewModel.start(separator) }
    SalaryEventsEffect(viewModel, snackbarHostState, onBack)
    val labels = salaryLineLabels()

    val content = state as? SalaryProfileUiState.Content
    EditorScaffold(
        title = stringResource(R.string.salary_profile_title),
        dirty = rememberIsDirty(content?.form),
        onBack = onBack,
        onSave = { viewModel.onSave(labels) },
        saving = false,
        saveEnabled = content?.canSave == true,
        showSave = content != null,
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        StateCrossfade(state) { current ->
            when (current) {
                SalaryProfileUiState.Loading -> CardsSkeleton(contentModifier)

                is SalaryProfileUiState.Content ->
                    SalaryProfileContent(
                        state = current,
                        actions =
                            SalaryProfileActions(
                                onChange = viewModel::onFormChange,
                                onDelete = viewModel::onDelete,
                                extras =
                                    SalaryExtraActions(
                                        onAdd = viewModel::onAddExtra,
                                        onEdit = viewModel::onEditExtra,
                                        onChange = viewModel::onExtraFormChange,
                                        onSave = viewModel::onSaveExtra,
                                        onDelete = viewModel::onDeleteExtra,
                                        onDismiss = viewModel::onDismissExtra,
                                    ),
                            ),
                        modifier = contentModifier,
                    )
            }
        }
    }
    if (duplicates.isNotEmpty()) {
        SalaryDuplicatesDialog(
            lines = duplicates,
            onRemove = viewModel::onRemoveDuplicates,
            onKeep = viewModel::onKeepDuplicates,
        )
    }
}

@Composable
private fun SalaryEventsEffect(
    viewModel: SalaryProfileViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val failedMessage = stringResource(R.string.error_plan_save_failed)
    val extraDeletedMessage = stringResource(R.string.salary_extra_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                SalaryProfileEvent.Done -> onBack()
                SalaryProfileEvent.Failed -> snackbarHostState.showSnackbar(failedMessage)
                is SalaryProfileEvent.ExtraDeleted -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showActionSnackbar(extraDeletedMessage, undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDeleteExtra(event.extra, event.index)
                }
            }
        }
    }
}

/** What the extra editor can trigger, bundled for detekt's LongParameterList. */
internal data class SalaryExtraActions(
    val onAdd: () -> Unit,
    val onEdit: (String) -> Unit,
    val onChange: (SalaryExtraForm) -> Unit,
    val onSave: () -> Unit,
    val onDelete: (String) -> Unit,
    val onDismiss: () -> Unit,
)

/** What the salary screen can trigger, bundled for detekt's LongParameterList. */
internal data class SalaryProfileActions(
    val onChange: (SalaryForm) -> Unit,
    val onDelete: () -> Unit,
    val extras: SalaryExtraActions,
)

@Composable
private fun SalaryProfileContent(
    state: SalaryProfileUiState.Content,
    actions: SalaryProfileActions,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    var confirmingDelete by remember { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.Space32),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        SegmentedTextTabs(
            options = SalaryInputMode.entries,
            selected = form.mode,
            label = { stringResource(modeLabel(it)) },
            onSelect = { actions.onChange(form.copy(mode = it)) },
        )
        SalaryBreakdownCard(state)
        if (form.mode == SalaryInputMode.GROSS) {
            GrossSections(form, state.invalid, actions.onChange)
        } else {
            NetSection(form, actions.onChange)
        }
        SubsidyMonthsSection(form, actions.onChange)
        MealSection(form, actions.onChange)
        SalaryExtrasSection(state, actions.extras.onAdd, actions.extras.onEdit)
        if (state.hasSavedProfile) {
            DestructiveTextButton(
                text = stringResource(R.string.salary_delete),
                onClick = { confirmingDelete = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
    state.extraEditor?.let { SalaryExtraSheet(it, actions.extras) }
    if (confirmingDelete) {
        DeleteSalaryProfileDialog(
            onConfirm = {
                confirmingDelete = false
                actions.onDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

private fun modeLabel(mode: SalaryInputMode): Int =
    if (mode == SalaryInputMode.GROSS) R.string.salary_mode_gross else R.string.salary_mode_net

@Composable
private fun DeleteSalaryProfileDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FinTrackAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.salary_delete_title)) },
        text = { Text(stringResource(R.string.salary_delete_message)) },
        confirmButton = { DestructiveTextButton(text = stringResource(R.string.salary_delete), onClick = onConfirm) },
        dismissButton = { DialogTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}

@Composable
private fun salaryLineLabels() =
    SalaryLineLabels(
        netSalary = stringResource(R.string.salary_line_net),
        duodecimos = stringResource(R.string.salary_line_duodecimos),
        meal = stringResource(R.string.salary_group_meal),
        holidaySubsidy = stringResource(R.string.salary_line_holiday),
        christmasSubsidy = stringResource(R.string.salary_line_christmas),
    )
