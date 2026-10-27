package com.joaoeoneves.fintrack.ui.settings.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.CategoryKind
import com.joaoeoneves.fintrack.domain.model.MAX_CATEGORY_NAME_LENGTH
import com.joaoeoneves.fintrack.domain.model.codePointLength
import com.joaoeoneves.fintrack.domain.model.limitInput
import com.joaoeoneves.fintrack.ui.common.FieldSupportingText
import com.joaoeoneves.fintrack.ui.common.asString
import com.joaoeoneves.fintrack.ui.common.categoryColorFor
import com.joaoeoneves.fintrack.ui.common.categoryColorKeys
import com.joaoeoneves.fintrack.ui.common.categoryIconFor
import com.joaoeoneves.fintrack.ui.common.categoryIconKeys
import com.joaoeoneves.fintrack.ui.common.displayName
import com.joaoeoneves.fintrack.ui.theme.LocalDarkTheme

private val OptionSize = 44.dp
private val SwatchSize = 36.dp
private val OptionSpacing = 8.dp
private val SectionSpacing = 16.dp
private val SelectedBorderWidth = 2.dp

@Composable
internal fun CategoryEditorDialog(
    state: CategoryEditorState,
    onSave: (CategoryDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(state.editingId, state.initial) { mutableStateOf(state.initial) }
    var submittedName by remember(state.editingId) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = if (state.editingId == null) R.string.category_new_title else R.string.category_edit_title
            Text(stringResource(title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                NameField(
                    draft = draft,
                    placeholder = state.takeIf { it.isBuiltIn }?.let { builtInDisplayName(state) },
                    error = state.nameError?.takeIf { draft.name == submittedName }?.asString(),
                    onNameChange = { draft = draft.copy(name = limitInput(draft.name, it, MAX_CATEGORY_NAME_LENGTH)) },
                )
                IconPicker(draft.iconKey, onSelected = { draft = draft.copy(iconKey = it) })
                ColorPicker(draft.colorKey, onSelected = { draft = draft.copy(colorKey = it) })
                KindPicker(draft.kind, onSelected = { draft = draft.copy(kind = it) })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    submittedName = draft.name
                    onSave(draft)
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The localized default name of the built-in being edited, shown as the name field's placeholder. */
@Composable
private fun builtInDisplayName(state: CategoryEditorState): String? =
    state.editingId?.let { id ->
        BuiltInCategories.byId(id)?.displayName
    }

@Composable
private fun NameField(
    draft: CategoryDraft,
    placeholder: String?,
    error: String?,
    onNameChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = draft.name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.category_field_name)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        isError = error != null,
        supportingText = {
            FieldSupportingText(error = error, length = draft.name.codePointLength(), max = MAX_CATEGORY_NAME_LENGTH)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PickerSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OptionSpacing)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(
    selectedKey: String,
    onSelected: (String) -> Unit,
) {
    PickerSection(stringResource(R.string.category_field_icon)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(OptionSpacing),
            verticalArrangement = Arrangement.spacedBy(OptionSpacing),
        ) {
            categoryIconKeys.forEachIndexed { index, key ->
                val selected = key == selectedKey
                val description = stringResource(R.string.cd_category_icon_option, index + 1)
                Box(
                    modifier =
                        Modifier
                            .size(OptionSize)
                            .clip(CircleShape)
                            .border(selectionBorder(selected), CircleShape)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelected(key) })
                            .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(categoryIconFor(key), contentDescription = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(
    selectedKey: String,
    onSelected: (String) -> Unit,
) {
    val isDark = LocalDarkTheme.current
    PickerSection(stringResource(R.string.category_field_color)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(OptionSpacing),
            verticalArrangement = Arrangement.spacedBy(OptionSpacing),
        ) {
            categoryColorKeys.forEachIndexed { index, key ->
                val selected = key == selectedKey
                val description = stringResource(R.string.cd_category_color_option, index + 1)
                Box(
                    modifier =
                        Modifier
                            .size(SwatchSize)
                            .clip(CircleShape)
                            .background(categoryColorFor(key, isDark))
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelected(key) })
                            .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun selectionBorder(selected: Boolean): BorderStroke =
    BorderStroke(
        SelectedBorderWidth,
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    )

@Composable
private fun KindPicker(
    selected: CategoryKind,
    onSelected: (CategoryKind) -> Unit,
) {
    PickerSection(stringResource(R.string.category_field_kind)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            CategoryKind.entries.forEachIndexed { index, kind ->
                SegmentedButton(
                    selected = kind == selected,
                    onClick = { onSelected(kind) },
                    shape = SegmentedButtonDefaults.itemShape(index, CategoryKind.entries.size),
                ) { Text(kind.label) }
            }
        }
    }
}
