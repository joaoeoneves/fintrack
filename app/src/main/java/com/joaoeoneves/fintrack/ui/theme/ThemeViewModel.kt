package com.joaoeoneves.fintrack.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        private val themeRepository: ThemeRepository,
    ) : ViewModel() {
        val themePreference: StateFlow<ThemePreference> =
            themeRepository.observeThemePreference().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemePreference.SYSTEM,
            )

        /**
         * Flips whichever theme is currently resolved/displayed to its opposite, regardless of
         * whether that resolved state came from an explicit preference or the system setting.
         * There is deliberately no "return to system default" option in this pass.
         */
        fun toggleTheme(currentlyResolvedDark: Boolean) {
            val next = if (currentlyResolvedDark) ThemePreference.LIGHT else ThemePreference.DARK
            viewModelScope.launch { themeRepository.setThemePreference(next) }
        }
    }
