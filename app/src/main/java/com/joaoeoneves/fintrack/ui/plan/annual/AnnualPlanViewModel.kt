package com.joaoeoneves.fintrack.ui.plan.annual

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.R
import com.joaoeoneves.fintrack.domain.usecase.ObserveAnnualPlan
import com.joaoeoneves.fintrack.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "AnnualPlanViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val KEY_TAB = "planTab"
private const val KEY_YEAR = "annualYear"

/** Earliest and latest year the Year view navigates to. */
internal const val MIN_ANNUAL_YEAR = 2000
internal const val MAX_ANNUAL_YEAR = 2100

/**
 * The Plan tab's Month | Year switch and the Year view. The selected tab and year live in the
 * [SavedStateHandle] (not in a route), so they survive process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnnualPlanViewModel
    @Inject
    constructor(
        private val observeAnnualPlan: ObserveAnnualPlan,
        private val savedStateHandle: SavedStateHandle,
        private val clock: Clock,
    ) : ViewModel() {
        /** The selected tab; the month view until the user switches. */
        val selectedTab: StateFlow<PlanTab> =
            savedStateHandle
                .getStateFlow(KEY_TAB, PlanTab.MONTH.token)
                .map { PlanTab.fromToken(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, PlanTab.fromToken(savedStateHandle[KEY_TAB]))

        private val year =
            savedStateHandle.getStateFlow(KEY_YEAR, LocalDate.now(clock).year)
        private val retryTrigger = MutableStateFlow(0)

        val uiState: StateFlow<AnnualPlanUiState> =
            combine(year, retryTrigger) { selected, _ -> selected }
                .flatMapLatest { selected ->
                    observeAnnualPlan(selected, clock.zone)
                        .map<_, AnnualPlanUiState> { plan ->
                            AnnualPlanUiState.Content(
                                year = selected,
                                plan = plan,
                                canGoPrevious = selected > MIN_ANNUAL_YEAR,
                                canGoNext = selected < MAX_ANNUAL_YEAR,
                            )
                        }.onStart { emit(AnnualPlanUiState.Loading) }
                        .catch { e ->
                            Log.w(TAG, "Failed to observe the annual plan", e)
                            emit(AnnualPlanUiState.Error(UiText.StringResource(R.string.error_generic_fallback)))
                        }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    initialValue = AnnualPlanUiState.Loading,
                )

        fun onSelectTab(tab: PlanTab) {
            savedStateHandle[KEY_TAB] = tab.token
        }

        fun onPreviousYear() = shiftYear(-1)

        fun onNextYear() = shiftYear(1)

        fun onRetry() {
            retryTrigger.value++
        }

        private fun shiftYear(delta: Int) {
            val target = (year.value + delta).coerceIn(MIN_ANNUAL_YEAR, MAX_ANNUAL_YEAR)
            savedStateHandle[KEY_YEAR] = target
        }
    }
