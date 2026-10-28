package com.joaoeoneves.fintrack.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.GoalTargetType
import com.joaoeoneves.fintrack.domain.model.MAX_GOAL_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.ui.common.CategoryChipFlow
import com.joaoeoneves.fintrack.ui.common.ConfirmRemoveDialog
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.ReadOnlyDateField
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.MoneyTextField
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The full-screen savings plan editor: name, monthly amount and schedule, target, save and delete. */
@Composable
fun SavingsPlanEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavingsPlanEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }
    LaunchedEffect(Unit) { viewModel.navigateBack.collect { onBack() } }
    DeletedSavingsPlanEffect(viewModel.deletedEvent, onBack)
    val content = state as? SavingsPlanEditorUiState.Content
    LargeTitleScaffold(
        title = stringResource(if (content?.isNew == false) R.string.savings_edit_plan else R.string.savings_new_plan),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
        bottomBar = {
            if (content != null) {
                PrimaryPillButton(
                    text = stringResource(R.string.action_save),
                    onClick = viewModel::onSave,
                    loading = content.saving,
                    modifier =
                        Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space12),
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (val current = state) {
            SavingsPlanEditorUiState.Loading -> Unit

            SavingsPlanEditorUiState.NotFound ->
                EmptyState(title = stringResource(R.string.error_state_title), modifier = contentModifier)

            is SavingsPlanEditorUiState.Content -> EditorBody(current, viewModel, contentModifier)
        }
    }
}

@Composable
private fun EditorBody(
    state: SavingsPlanEditorUiState.Content,
    viewModel: SavingsPlanEditorViewModel,
    modifier: Modifier,
) {
    val draft = state.draft
    var confirmingDelete by remember { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
    ) {
        NameField(draft, viewModel::onNameChange)
        MonthlyFields(state, viewModel)
        TargetFields(draft, viewModel)
        if (!state.isNew) {
            DestructiveTextButton(
                text = stringResource(R.string.savings_delete_plan),
                onClick = { confirmingDelete = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
    if (confirmingDelete) {
        ConfirmRemoveDialog(
            title = stringResource(R.string.savings_delete_plan),
            message = stringResource(R.string.savings_delete_message, draft.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmingDelete = false
                viewModel.onDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun NameField(
    draft: SavingsPlanDraft,
    onChange: (String) -> Unit,
) {
    val invalid = draft.showErrors && !draft.nameValid
    MinimalTextField(
        value = draft.name,
        onValueChange = onChange,
        label = stringResource(R.string.goal_field_name),
        isError = invalid,
        supportingText = {
            FieldSupportingText(
                error = stringResource(R.string.error_name_required).takeIf { invalid },
                length = draft.name.codePointLength(),
                max = MAX_GOAL_NAME_LENGTH,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The monthly amount; once there is one, also its day, category and whether it is recorded automatically. */
@Composable
private fun MonthlyFields(
    state: SavingsPlanEditorUiState.Content,
    viewModel: SavingsPlanEditorViewModel,
) {
    val draft = state.draft
    MoneyTextField(
        cents = draft.monthlyCents,
        onCentsChange = viewModel::onMonthlyChange,
        label = stringResource(R.string.savings_monthly_amount),
        modifier = Modifier.fillMaxWidth(),
    )
    if (!draft.hasMonthly) return
    MinimalTextField(
        value = draft.dayText,
        onValueChange = viewModel::onDayChange,
        label = stringResource(R.string.savings_day),
        isError = draft.showErrors && !draft.dayValid,
        supportingText = {
            if (draft.showErrors && !draft.dayValid) Text(stringResource(R.string.error_recurring_day_invalid))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    CategoryChipFlow(
        options = state.categories,
        selected = state.categories.firstOrNull { it.id == draft.categoryId },
        error = stringResource(R.string.error_category_required).takeIf { draft.showErrors && !draft.categoryValid },
        onSelected = { viewModel.onCategoryChange(it.id) },
    )
    SettingsSwitchRow(
        title = stringResource(R.string.savings_auto_post),
        checked = draft.autoPost,
        onCheckedChange = viewModel::onAutoPostChange,
        supportingText = stringResource(R.string.recurring_auto_post_hint),
    )
}

/** The optional target with its Per year / Total choice; a Total target also has a date and an initial amount. */
@Composable
private fun TargetFields(
    draft: SavingsPlanDraft,
    viewModel: SavingsPlanEditorViewModel,
) {
    MoneyTextField(
        cents = draft.targetCents,
        onCentsChange = viewModel::onTargetChange,
        label = stringResource(R.string.savings_target),
        modifier = Modifier.fillMaxWidth(),
    )
    SegmentedTextTabs(
        options = GoalTargetType.entries,
        selected = draft.type,
        label = { stringResource(it.labelRes()) },
        onSelect = viewModel::onTypeChange,
    )
    if (draft.type == GoalTargetType.TOTAL) TotalFields(draft, viewModel)
}

@Composable
private fun TotalFields(
    draft: SavingsPlanDraft,
    viewModel: SavingsPlanEditorViewModel,
) {
    val zone = ZoneId.systemDefault()
    val target = draft.targetDate
    if (target == null) {
        GhostButton(
            text = stringResource(R.string.goal_target_date_add),
            onClick = { viewModel.onTargetDateChange(draft.startDate.plusYears(1)) },
        )
    } else {
        ReadOnlyDateField(
            date = target.toNoonInstant(zone),
            onDateSelected = { viewModel.onTargetDateChange(it.atZone(zone).toLocalDate()) },
            modifier = Modifier.fillMaxWidth(),
            zone = zone,
            labelRes = R.string.goal_field_target_date,
        )
        if (draft.showErrors && !draft.datesValid) {
            Text(stringResource(R.string.goal_error_dates), color = FinTrackTheme.colors.negative)
        }
        GhostButton(
            text = stringResource(R.string.goal_target_date_clear),
            onClick = { viewModel.onTargetDateChange(null) },
        )
    }
    MoneyTextField(
        cents = draft.initialCents,
        onCentsChange = viewModel::onInitialChange,
        label = stringResource(R.string.goal_field_initial),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun GoalTargetType.labelRes(): Int =
    when (this) {
        GoalTargetType.ANNUAL -> R.string.savings_per_year
        GoalTargetType.TOTAL -> R.string.savings_total
    }

private fun LocalDate.toNoonInstant(zone: ZoneId) = atTime(LocalTime.NOON).atZone(zone).toInstant()
