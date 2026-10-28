package com.joaoeoneves.fintrack.ui.plan.salary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.expense.addedit.decimalSeparatorFor
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
    val failedMessage = stringResource(R.string.error_plan_save_failed)
    val separator = decimalSeparatorFor(currentLocale())
    LaunchedEffect(separator) { viewModel.start(separator) }
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                SalaryProfileEvent.Done -> onBack()
                SalaryProfileEvent.Failed -> snackbarHostState.showSnackbar(failedMessage)
            }
        }
    }
    val labels =
        SalaryLineLabels(
            netSalary = stringResource(R.string.salary_line_net),
            duodecimos = stringResource(R.string.salary_line_duodecimos),
            meal = stringResource(R.string.salary_group_meal),
            subsidy = stringResource(R.string.salary_line_subsidy),
            flex = stringResource(R.string.salary_group_flex),
            profitShare = stringResource(R.string.salary_breakdown_profit_share),
            bonus = stringResource(R.string.salary_field_bonus),
        )

    LargeTitleScaffold(
        title = stringResource(R.string.salary_profile_title),
        onBack = onBack,
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (val current = state) {
            SalaryProfileUiState.Loading ->
                Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            is SalaryProfileUiState.Content ->
                SalaryProfileContent(
                    state = current,
                    actions =
                        SalaryProfileActions(
                            onChange = viewModel::onFormChange,
                            onSave = { viewModel.onSave(labels) },
                            onDelete = viewModel::onDelete,
                        ),
                    modifier = contentModifier,
                )
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

/** What the salary screen can trigger, bundled for detekt's LongParameterList. */
internal data class SalaryProfileActions(
    val onChange: (SalaryForm) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
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
            NetSection(form, state.invalid, actions.onChange)
        }
        SubsidyMonthsSection(form, actions.onChange)
        MealSection(form, state.invalid, actions.onChange)
        FlexSection(form, state.invalid, actions.onChange)
        ExtrasSection(form, state.invalid, actions.onChange)
        PrimaryPillButton(
            text = stringResource(R.string.action_save),
            onClick = actions.onSave,
            enabled = state.canSave,
            modifier = Modifier.padding(top = Dimens.Space24),
        )
        if (state.hasSavedProfile) {
            DestructiveTextButton(
                text = stringResource(R.string.salary_delete),
                onClick = { confirmingDelete = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
