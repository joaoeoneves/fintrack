package com.joaoeoneves.fintrack.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackDropdownMenu
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackMenuItem
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

private val SortButtonSize = 56.dp

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
        placeholder = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
 * Sort control: a round icon button that opens a dropdown of all [options], with the selected one
 * ticked. Icon-only so the search field next to it keeps its width; the current order is the
 * button's content description.
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
        val current = optionLabel(selected)
        Box(
            modifier =
                Modifier
                    .size(SortButtonSize)
                    .clip(CircleShape)
                    .background(FinTrackTheme.colors.surface2)
                    .clickable { expanded = true }
                    .semantics { contentDescription = current },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = null,
                tint = FinTrackTheme.colors.textSecondary,
            )
        }
        FinTrackDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                FinTrackMenuItem(
                    text = optionLabel(option),
                    selected = option == selected,
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
