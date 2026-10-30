package com.joaoeoneves.fintrack.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.security.AppLockController
import com.joaoeoneves.fintrack.data.security.DeviceLockChecker
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import com.joaoeoneves.fintrack.ui.lock.LockPromptResult
import com.joaoeoneves.fintrack.ui.lock.findFragmentActivity
import com.joaoeoneves.fintrack.ui.lock.showAppLockPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** What the "Lock the app" row shows: whether it is on, and whether the device can authenticate at all. */
data class AppLockSettingState(
    val enabled: Boolean = false,
    val available: Boolean = true,
)

/** Backs the "Lock the app" row. Turning it on needs one successful authentication; turning it off needs none. */
@HiltViewModel
class AppLockSettingViewModel
    @Inject
    constructor(
        private val controller: AppLockController,
        private val checker: DeviceLockChecker,
    ) : ViewModel() {
        private val available = MutableStateFlow(checker.isDeviceSecure())
        private val requests = Channel<Unit>(Channel.BUFFERED)

        val uiState: StateFlow<AppLockSettingState> =
            combine(controller.enabled, available) { enabled, available ->
                AppLockSettingState(enabled = enabled, available = available)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                AppLockSettingState(available = available.value),
            )

        /** Emits once per request to authenticate before the lock is turned on. */
        val requestEnableAuth = requests.receiveAsFlow()

        /** Re-reads whether the device has a screen lock (the user may have just set or removed one). */
        fun refreshAvailability() {
            available.value = checker.isDeviceSecure()
        }

        fun onToggle(want: Boolean) {
            if (want) {
                requests.trySend(Unit)
            } else {
                viewModelScope.launch { controller.setEnabled(false) }
            }
        }

        fun onEnableAuthResult(success: Boolean) {
            if (success) viewModelScope.launch { controller.setEnabled(true) }
        }
    }

/** The Settings "Security" group with the app-lock switch. */
@Composable
fun AppLockSection(
    modifier: Modifier = Modifier,
    viewModel: AppLockSettingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findFragmentActivity()
    val promptTitle = stringResource(R.string.lock_enable_prompt_title)
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshAvailability()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel, activity, promptTitle) {
        viewModel.requestEnableAuth.collect {
            activity?.showAppLockPrompt(promptTitle) { result ->
                if (result == LockPromptResult.UNAVAILABLE) viewModel.refreshAvailability()
                viewModel.onEnableAuthResult(result == LockPromptResult.SUCCESS)
            }
        }
    }
    SettingsGroup(label = stringResource(R.string.settings_section_security), modifier = modifier) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_app_lock),
            checked = state.enabled && state.available,
            onCheckedChange = viewModel::onToggle,
            supportingText =
                stringResource(
                    if (state.available) R.string.settings_app_lock_hint else R.string.settings_app_lock_unavailable,
                ),
            enabled = state.available,
        )
    }
}
