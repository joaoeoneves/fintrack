package com.joaoeoneves.fintrack.ui.plan.envelopes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.CategoryId
import com.joaoeoneves.fintrack.domain.model.MAX_GROUP_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.common.icon
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.EditorScaffold
import com.joaoeoneves.fintrack.ui.designsystem.EmptyState
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackBottomSheet
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackIconButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackSnackbarHost
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.MinimalTextField
import com.joaoeoneves.fintrack.ui.designsystem.SectionTitle
import com.joaoeoneves.fintrack.ui.designsystem.rememberIsDirty
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** What the editor's controls trigger, bundled for detekt's LongParameterList. */
private class EditorActions(
    val onNameChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val categories: CategoryActions,
)

/** What the editor's category list triggers. */
private class CategoryActions(
    val onAdd: (CategoryId) -> Unit,
    val onRemove: (CategoryId) -> Unit,
)

/** The full-screen group editor: name, categories, save and delete. */
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
                onSave = viewModel::onSave,
                onDelete = viewModel::onDelete,
                categories =
                    CategoryActions(
                        onAdd = viewModel::onAddCategory,
                        onRemove = viewModel::onRemoveCategory,
                    ),
            )
        }
    val content = state as? EnvelopeEditorUiState.Content
    EditorScaffold(
        title =
            stringResource(
                if (content?.isNew == false) R.string.envelope_edit_title else R.string.envelope_new_title,
            ),
        dirty = rememberIsDirty(content?.draft),
        onBack = onBack,
        onSave = actions.onSave,
        saving = content?.saving == true,
        showSave = content != null,
        modifier = modifier,
        snackbarHost = { FinTrackSnackbarHost(snackbarHostState) },
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
        CategoriesSection(state, actions)
        if (!state.isNew) {
            DestructiveTextButton(
                text = stringResource(R.string.envelope_delete),
                onClick = actions.onDelete,
                modifier = Modifier.align(Alignment.CenterHorizontally),
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
                onRemove = { actions.categories.onRemove(member.category.id) },
            )
        }
        GhostButton(
            text = stringResource(R.string.envelope_add_category),
            onClick = { adding = true },
            enabled = state.addable.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.Space8),
        )
        Text(
            text = stringResource(R.string.envelope_saving_hidden_hint),
            style = MaterialTheme.typography.bodySmall,
            color = FinTrackTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Dimens.Space4),
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
    onRemove: () -> Unit,
) {
    val name = member.category.displayName
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Space4),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = member.category.icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            member.movedFromName?.let {
                Text(
                    text = stringResource(R.string.envelope_leaves_other, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
        }
        FinTrackIconButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.action_remove) + " " + name,
            onClick = onRemove,
        )
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
                            text = stringResource(R.string.envelope_leaves_other, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = FinTrackTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
