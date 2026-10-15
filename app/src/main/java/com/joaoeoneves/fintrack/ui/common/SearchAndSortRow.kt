package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R

/**
 * Search text field shared between the expense and income list screens.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Sort dropdown shared between the expense and income list screens. Generic over the sort option
 * type [T] so each screen's own enum (`SortOption`/`IncomeSortOption`) can reuse the same UI and
 * dropdown wiring instead of duplicating it per screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SortDropdown(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            readOnly = true,
            value = optionLabel(selected),
            onValueChange = {},
            label = { Text(stringResource(R.string.sort_by_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
