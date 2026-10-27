package com.joaoeoneves.fintrack.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.ui.designsystem.Hairline
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsRow
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

/** JSON backup group: export in both modes, restore only for guests (local data to replace), and the wipe. */
@Composable
fun BackupSection(
    isGuest: Boolean,
    onExportClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDeleteAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_backup), modifier = modifier) {
        Text(
            text =
                stringResource(
                    if (isGuest) R.string.settings_backup_guest_hint else R.string.settings_backup_signed_in_hint,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = FinTrackTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = Dimens.Space4),
        )
        SettingsRow(title = stringResource(R.string.settings_export_backup), onClick = onExportClick)
        if (isGuest) {
            Hairline()
            SettingsRow(title = stringResource(R.string.settings_restore_backup), onClick = onRestoreClick)
        }
        Hairline()
        SettingsRow(
            title = stringResource(R.string.settings_delete_all_data),
            onClick = onDeleteAllClick,
            titleColor = FinTrackTheme.colors.negative,
        )
    }
}
