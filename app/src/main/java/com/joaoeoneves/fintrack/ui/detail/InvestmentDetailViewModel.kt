package com.joaoeoneves.fintrack.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.InstrumentAmount
import com.joaoeoneves.fintrack.domain.model.InvestmentMonth
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.canGoToNextMonth
import com.joaoeoneves.fintrack.domain.model.canGoToNextYear
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.usecase.ObserveInvestmentHistory
import com.joaoeoneves.fintrack.ui.common.period.PeriodActions
import com.joaoeoneves.fintrack.ui.common.period.PeriodSelection
import com.joaoeoneves.fintrack.ui.common.period.asMonthOrYearPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

// The route argument (InvestmentDetail), read from the SavedStateHandle.
private const val ARG_PERIOD = "period"

// The selected period (a month or a year), kept across process death.
private const val KEY_SELECTED_PERIOD = "selectedPeriod"

/** Months of the chart on the month scale. */
const val INVESTMENT_MONTHS = 12

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** The investments page: its bars, totals, the selected month's (or year's) split and movements. */
sealed interface InvestmentDetailUiState {
    data object Loading : InvestmentDetailUiState

    data object Error : InvestmentDetailUiState

    /**
     * @property period the selected month or year.
     * @property months the bars, oldest first (12 months on the month scale, the year's months on the year scale).
     * @property selectedIndex the selected month's bar, [NO_SELECTION] on the year scale.
     * @property amountCents invested in the selected month, or in the selected year.
     * @property instruments the split of that month or year per instrument, biggest first.
     * @property year the year of [yearCents]; [allTimeCents] is everything invested up to the last charted month.
     * @property movements the movements of the selected month or year.
     */
    data class Content(
        val period: Period,
        val canGoToNextMonth: Boolean,
        val months: List<InvestmentMonth>,
        val selectedIndex: Int,
        val amountCents: Long,
        val instruments: List<InstrumentAmount>,
        val year: Int,
        val yearCents: Long,
        val allTimeCents: Long,
        val isCurrentMonth: Boolean,
        val movements: List<Expense>,
    ) : InvestmentDetailUiState {
        /** The selected month's bar, `null` on the year scale. */
        val selected: InvestmentMonth? get() = months.getOrNull(selectedIndex)
    }
}

/**
 * The investments page (reached from Home's "Invested" row), on the "Mês | Ano" switch. Month: monthly bars ending at
 * the route's month, the selected month's split per instrument and its movements. Year: the months of the selected
 * calendar year, the year's split and movements. The selection starts at the route's period.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InvestmentDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val observeInvestmentHistory: ObserveInvestmentHistory,
        private val clock: Clock,
    ) : ViewModel() {
        private val zone: ZoneId = clock.zone
        private val routePeriod: Period =
            decodePeriod(savedStateHandle[ARG_PERIOD] ?: Period.CurrentMonth.encode(), clock.instant(), zone)
                .asMonthOrYearPeriod()
        private val routeEnd: YearMonth = routeEndOf(routePeriod, clock.instant(), zone)
        private val selection =
            PeriodSelection(savedStateHandle, KEY_SELECTED_PERIOD, routePeriod.encode(), clock)

        /** The period selector's callbacks (month and year scales). */
        val periodActions: PeriodActions =
            PeriodActions(
                onPrevious = selection::previous,
                onNext = selection::next,
                onSelect = { selection.select(it.asMonthOrYearPeriod()) },
                onSelectScale = selection::selectScale,
            )

        val uiState: StateFlow<InvestmentDetailUiState> =
            selection.token
                .flatMapLatest { token -> contentOf(token) }
                .catch { emit(InvestmentDetailUiState.Error) }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    InvestmentDetailUiState.Loading,
                )

        /** Selects [month] on the month scale (one of the chart's months; anything else is ignored). */
        fun onSelectMonth(month: YearMonth) {
            val now = clock.instant()
            val period = decodePeriod(selection.token.value, now, zone)
            if (period is Period.Year || month !in chartMonthsOf(period, routeEnd, INVESTMENT_MONTHS, now, zone)) return
            selection.select(Period.ofMonth(month, now, zone))
        }

        private fun Expense.month(): YearMonth = YearMonth.from(date.atZone(zone))

        private fun contentOf(token: String): Flow<InvestmentDetailUiState> {
            val now = clock.instant()
            val period = decodePeriod(token, now, zone)
            val window = chartMonthsOf(period, routeEnd, INVESTMENT_MONTHS, now, zone)
            val canGoNext =
                if (period is Period.Year) period.canGoToNextYear(now, zone) else period.canGoToNextMonth(now, zone)
            return observeInvestmentHistory(window.last(), window.size, zone).map { overview ->
                val history = overview.history
                val selected = period.anchorMonth(now, zone)
                val month = history.monthOf(selected ?: window.last())
                InvestmentDetailUiState.Content(
                    period = period,
                    canGoToNextMonth = canGoNext,
                    months = history.months,
                    selectedIndex = selected?.let { window.indexOf(it) } ?: NO_SELECTION,
                    amountCents = if (selected == null) history.totalCents else month?.totalCents ?: 0L,
                    instruments = if (selected == null) history.instruments else month?.instruments.orEmpty(),
                    year = window.last().year,
                    yearCents = overview.yearCents,
                    allTimeCents = overview.allTimeCents,
                    isCurrentMonth = selected == currentYearMonth(now, zone),
                    movements = history.movements.filter { selected == null || it.month() == selected },
                )
            }
        }
    }
