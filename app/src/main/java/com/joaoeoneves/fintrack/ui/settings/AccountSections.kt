package com.joaoeoneves.fintrack.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.ui.designsystem.DestructiveTextButton
import com.joaoeoneves.fintrack.ui.designsystem.FinTrackAlertDialog
import com.joaoeoneves.fintrack.ui.designsystem.GhostButton
import com.joaoeoneves.fintrack.ui.designsystem.PrimaryPillButton
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.theme.Dimens
import com.joaoeoneves.fintrack.ui.theme.FinTrackTheme

@Composable
fun AccountSection(
    currentUser: AuthUser?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_account), modifier = modifier) {
        val signedInFallback = stringResource(R.string.settings_signed_in_fallback)
        Column(modifier = Modifier.padding(vertical = Dimens.Space4)) {
            Text(
                text = currentUser?.displayName ?: currentUser?.email ?: signedInFallback,
                style = MaterialTheme.typography.bodyLarge,
            )
            val email = currentUser?.email
            if (email != null && currentUser.displayName != null) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinTrackTheme.colors.textSecondary,
                )
            }
        }
        DestructiveTextButton(text = stringResource(R.string.action_sign_out), onClick = onSignOut)
    }
}

/** The account group in guest mode: explains the data is device-only and offers sign-in or leaving. */
@Composable
fun GuestAccountSection(
    onSignInToSync: () -> Unit,
    onLeaveGuestMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmLeave by remember { mutableStateOf(false) }

    SettingsGroup(label = stringResource(R.string.settings_section_account), modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_guest_account),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = Dimens.Space12),
        )
        PrimaryPillButton(text = stringResource(R.string.action_sign_in_to_sync), onClick = onSignInToSync)
        GhostButton(
            text = stringResource(R.string.action_leave_guest_mode),
            onClick = { confirmLeave = true },
            modifier = Modifier.padding(top = Dimens.Space8),
        )
    }

    if (confirmLeave) {
        FinTrackAlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.leave_guest_confirm_title)) },
            text = { Text(stringResource(R.string.leave_guest_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        onLeaveGuestMode()
                    },
                ) {
                    Text(stringResource(R.string.action_leave_guest_mode))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
