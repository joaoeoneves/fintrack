package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val SortChipMinHeight = 40.dp
private val SortIconSize = 18.dp

/**
 * Search text field shared between the expense and income list screens: a rounded filled field
 * whose placeholder is [label].
 */
@Composable
fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val surface2 = FinTrackTheme.colors.surface2
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = surface2,
                unfocusedContainerColor = surface2,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = FinTrackTheme.colors.textSecondary,
                unfocusedPlaceholderColor = FinTrackTheme.colors.textSecondary,
                focusedLeadingIconColor = FinTrackTheme.colors.textSecondary,
                unfocusedLeadingIconColor = FinTrackTheme.colors.textSecondary,
            ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Sort control shared between the expense and income list screens: a compact chip showing the
 * selected option's label that opens a dropdown of all [options]. Generic over the sort option
 * type [T] so each screen's own enum (`SortOption`) can reuse it.
 */
@Composable
fun <T> SortDropdown(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = SortChipMinHeight)
                    .clip(CircleShape)
                    .background(FinTrackTheme.colors.surface2)
                    .clickable { expanded = true }
                    .padding(start = Dimens.Space12, end = Dimens.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = null,
                tint = FinTrackTheme.colors.textSecondary,
                modifier = Modifier.padding(end = Dimens.Space8).size(SortIconSize),
            )
            Text(
                text = optionLabel(selected),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = FinTrackTheme.colors.surface2,
        ) {
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
