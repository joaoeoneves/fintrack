package com.joaoeoneves.fintrack.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.data.preferences.StartChecklistState
import com.joaoeoneves.fintrack.data.preferences.StartChecklistStore
import com.joaoeoneves.fintrack.ui.designsystem.SettingsGroup
import com.joaoeoneves.fintrack.ui.designsystem.SettingsSwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Backs the "Mostrar Começar" setting: brings Home's "Get started" card back after it was dismissed. */
@HiltViewModel
class StartChecklistSettingViewModel
    @Inject
    constructor(
        private val store: StartChecklistStore,
    ) : ViewModel() {
        val shown: StateFlow<Boolean> =
            store.state
                .map { it == StartChecklistState.ACTIVE }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), false)

        fun setShown(shown: Boolean) {
            viewModelScope.launch {
                store.set(if (shown) StartChecklistState.ACTIVE else StartChecklistState.DISMISSED)
            }
        }
    }

/** The Settings row that shows or hides Home's "Get started" card. */
@Composable
fun StartChecklistSection(
    modifier: Modifier = Modifier,
    viewModel: StartChecklistSettingViewModel = hiltViewModel(),
) {
    val shown = viewModel.shown.collectAsStateWithLifecycle().value
    SettingsGroup(label = stringResource(R.string.settings_section_home), modifier = modifier) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_show_start),
            checked = shown,
            onCheckedChange = viewModel::setShown,
            supportingText = stringResource(R.string.settings_show_start_hint),
        )
    }
}
