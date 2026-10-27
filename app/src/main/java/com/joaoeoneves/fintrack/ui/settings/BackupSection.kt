package com.joaoeoneves.fintrack.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaoeoneves.fintrack.R

/** JSON backup card: export in both modes, restore only for guests (which have local data to replace). */
@Composable
fun BackupSection(
    isGuest: Boolean,
    onExportClick: () -> Unit,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            SectionHeader(stringResource(R.string.settings_section_backup), Icons.Filled.ImportExport)
            Text(
                text =
                    stringResource(
                        if (isGuest) R.string.settings_backup_guest_hint else R.string.settings_backup_signed_in_hint,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            BackupRow(text = stringResource(R.string.settings_export_backup), onClick = onExportClick)
            if (isGuest) {
                BackupRow(text = stringResource(R.string.settings_restore_backup), onClick = onRestoreClick)
            }
        }
    }
}

@Composable
private fun BackupRow(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
