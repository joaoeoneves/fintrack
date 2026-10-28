package com.joaoeoneves.fintrack.ui.plan.envelopes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.SalaryComponent
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.ui.common.ConfirmRemoveDialog
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.icon
import com.joaoeoneves.fintrack.ui.designsystem.CategoryChip
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.LargeTitleScaffold
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.MoneyTextField
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.SegmentedTextTabs
import com.joaoeoneves.fintrack.ui.plan.label
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val LimitFieldWidth = 140.dp

/** What the editor's controls trigger, bundled for detekt's LongParameterList. */
private class EditorActions(
    val onNameChange: (String) -> Unit,
    val budget: BudgetActions,
    val onFundedByChange: (String?) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val categories: CategoryActions,
)

/** What the editor's budget mode and amount trigger. */
private class BudgetActions(
    val onModeChange: (EnvelopeBudgetMode) -> Unit,
    val onAmountChange: (Long?) -> Unit,
)

/** What the editor's category list triggers. */
private class CategoryActions(
    val onAdd: (CategoryId) -> Unit,
    val onRemove: (CategoryId) -> Unit,
    val onLimitChange: (CategoryId, Long?) -> Unit,
)

/** The full-screen envelope editor: name, budget mode, categories, funding line, save and delete. */
@Composable
fun EnvelopeEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EnvelopeEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { snackbarHostState.showSnackbar(it.asString(resources)) }
    }
    LaunchedEffect(Unit) { viewModel.navigateBack.collect { onBack() } }
    DeletedEnvelopeEffect(viewModel.deletedEvent, onBack)
    val actions =
        remember(viewModel) {
            EditorActions(
                onNameChange = viewModel::onNameChange,
                budget = BudgetActions(viewModel::onModeChange, viewModel::onAmountChange),
                onFundedByChange = viewModel::onFundedByChange,
                onSave = viewModel::onSave,
                onDelete = viewModel::onDelete,
                categories =
                    CategoryActions(
                        onAdd = viewModel::onAddCategory,
                        onRemove = viewModel::onRemoveCategory,
                        onLimitChange = viewModel::onLimitChange,
                    ),
            )
        }
    val content = state as? EnvelopeEditorUiState.Content
    LargeTitleScaffold(
        title =
            stringResource(
                if (content?.isNew == false) R.string.envelope_edit_title else R.string.envelope_new_title,
            ),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
        bottomBar = {
            if (content != null) {
                PrimaryPillButton(
                    text = stringResource(R.string.action_save),
                    onClick = actions.onSave,
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
            EnvelopeEditorUiState.Loading -> Unit

            EnvelopeEditorUiState.NotFound ->
                EmptyState(title = stringResource(R.string.error_state_title), modifier = contentModifier)

            is EnvelopeEditorUiState.Content -> EditorBody(current, actions, contentModifier)
        }
    }
}

@Composable
private fun EditorBody(
    state: EnvelopeEditorUiState.Content,
    actions: EditorActions,
    modifier: Modifier,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
    ) {
        MinimalTextField(
            value = state.draft.name,
            onValueChange = actions.onNameChange,
            label = stringResource(R.string.envelope_name),
            isError = state.nameError != null,
            supportingText = {
                FieldSupportingText(
                    error = state.nameError?.asString(),
                    length = state.draft.name.codePointLength(),
                    max = MAX_GROUP_NAME_LENGTH,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        BudgetModeSection(state, actions)
        CategoriesSection(state, actions)
        FundingSection(state, actions.onFundedByChange)
        if (!state.isNew) {
            DestructiveTextButton(
                text = stringResource(R.string.envelope_delete),
                onClick = { confirmingDelete = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
    if (confirmingDelete) {
        ConfirmRemoveDialog(
            title = stringResource(R.string.envelope_delete),
            message = stringResource(R.string.envelope_delete_confirm, state.draft.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmingDelete = false
                actions.onDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun EnvelopeBudgetMode.label(): String =
    stringResource(
        when (this) {
            EnvelopeBudgetMode.SINGLE -> R.string.envelope_mode_single
            EnvelopeBudgetMode.PER_CATEGORY -> R.string.envelope_mode_per_category
            EnvelopeBudgetMode.NONE -> R.string.envelope_mode_none
        },
    )

@Composable
private fun BudgetModeSection(
    state: EnvelopeEditorUiState.Content,
    actions: EditorActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
        SegmentedTextTabs(
            options = EnvelopeBudgetMode.entries,
            selected = state.draft.mode,
            label = { it.label() },
            onSelect = actions.budget.onModeChange,
        )
        if (state.draft.mode == EnvelopeBudgetMode.NONE) {
            Text(
                text = stringResource(R.string.envelope_mode_none_hint),
                style = MaterialTheme.typography.bodySmall,
                color = FinTrackTheme.colors.textSecondary,
            )
        }
        if (state.draft.mode == EnvelopeBudgetMode.SINGLE) {
            MoneyTextField(
                cents = state.draft.amountCents ?: 0L,
                onCentsChange = { actions.budget.onAmountChange(it.takeIf { cents -> cents > 0L }) },
                label = stringResource(R.string.envelope_amount),
                isError = state.amountError != null,
                supportingText = state.amountError?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CategoriesSection(
    state: EnvelopeEditorUiState.Content,
    actions: EditorActions,
) {
    var adding by remember { mutableStateOf(false) }
    Column {
        SectionTitle(title = stringResource(R.string.envelope_categories))
        state.members.forEach { member ->
            MemberRow(
                member = member,
                perCategory = state.draft.mode == EnvelopeBudgetMode.PER_CATEGORY,
                onLimitChange = { actions.categories.onLimitChange(member.category.id, it) },
                onRemove = { actions.categories.onRemove(member.category.id) },
            )
        }
        GhostButton(
            text = stringResource(R.string.envelope_add_category),
            onClick = { adding = true },
            enabled = state.addable.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.Space8),
        )
    }
    if (adding) {
        AddCategorySheet(
            addable = state.addable,
            onPick = {
                actions.categories.onAdd(it)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun MemberRow(
    member: MemberUi,
    perCategory: Boolean,
    onLimitChange: (Long?) -> Unit,
    onRemove: () -> Unit,
) {
    val name = member.category.displayName
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = member.category.icon, contentDescription = null)
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (perCategory) {
            MoneyTextField(
                cents = member.limitCents ?: 0L,
                onCentsChange = { onLimitChange(it.takeIf { cents -> cents > 0L }) },
                label = name,
                modifier = Modifier.width(LimitFieldWidth),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove) + " " + name)
        }
    }
}

@Composable
private fun AddCategorySheet(
    addable: List<AddableCategoryUi>,
    onPick: (CategoryId) -> Unit,
    onDismiss: () -> Unit,
) {
    FinTrackBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.envelope_add_category),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Dimens.Space12),
        )
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            addable.forEach { item ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item.category.id) }
                            .padding(vertical = Dimens.Space12),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = item.category.icon, contentDescription = null)
                    Text(
                        text = item.category.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    item.inEnvelopeName?.let {
                        Text(
                            text = stringResource(R.string.envelope_in_other, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = FinTrackTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FundingSection(
    state: EnvelopeEditorUiState.Content,
    onFundedByChange: (String?) -> Unit,
) {
    if (state.fundingOptions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space8)) {
        SectionTitle(title = stringResource(R.string.envelope_paid_by))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            CategoryChip(
                label = stringResource(R.string.envelope_paid_by_none),
                selected = state.draft.fundedByLineId == null,
                onClick = { onFundedByChange(null) },
            )
            state.fundingOptions.forEach { option ->
                Column {
                    CategoryChip(
                        label = option.component.takeIf { it != SalaryComponent.EXTRA }?.label() ?: option.label,
                        selected = state.draft.fundedByLineId == option.lineId,
                        onClick = { onFundedByChange(option.lineId) },
                    )
                    option.usedByEnvelopeName?.let {
                        Text(
                            text = stringResource(R.string.envelope_in_other, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = FinTrackTheme.colors.textSecondary,
                            modifier = Modifier.padding(start = Dimens.Space16),
                        )
                    }
                }
            }
        }
    }
}
