package com.joaoeoneves.fintrack.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** The app's dropdown menu: medium shape on the raised surface. Fill it with [FinTrackMenuItem]s. */
@Composable
fun FinTrackDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        containerColor = FinTrackTheme.colors.surface2,
        content = content,
    )
}

/**
 * One row of a [FinTrackDropdownMenu]. A [selected] choice of a single-choice menu gets a trailing accent check;
 * leave it `false` for a plain action.
 */
@Composable
fun FinTrackMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        trailingIcon =
            if (selected) {
                { Icon(Icons.Default.Check, contentDescription = null, tint = FinTrackTheme.colors.accentText) }
            } else {
                null
            },
    )
}

/** A tappable row with a leading checkbox, the [label] filling the width and an optional [trailing] value. */
@Composable
fun FinTrackCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.SettingsRowMinHeight)
                .clickable(role = Role.Checkbox) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = label, modifier = Modifier.weight(1f))
        trailing?.let { Text(text = it, modifier = Modifier.padding(start = Dimens.Space8)) }
    }
}
