package com.joaoeoneves.fintrack.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.repository.CurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Read-only view of the selected display currency, purely to drive the `CompositionLocalProvider`
 * in `MainActivity`. Writes are owned by `SettingsViewModel`.
 */
@HiltViewModel
class CurrencyViewModel
    @Inject
    constructor(
        currencyRepository: CurrencyRepository,
    ) : ViewModel() {
        val currency: StateFlow<CurrencyOption> =
            currencyRepository.observeCurrency().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CurrencyOption.EUR,
            )
    }
