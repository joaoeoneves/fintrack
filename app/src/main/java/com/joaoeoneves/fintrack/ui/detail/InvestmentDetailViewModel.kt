package com.joaoeoneves.fintrack.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.InvestmentMonth
import com.joaoeoneves.fintrack.domain.model.Period
import com.joaoeoneves.fintrack.domain.model.anchorMonth
import com.joaoeoneves.fintrack.domain.model.currentYearMonth
import com.joaoeoneves.fintrack.domain.model.decodePeriod
import com.joaoeoneves.fintrack.domain.model.encode
import com.joaoeoneves.fintrack.domain.usecase.InvestmentOverview
import com.joaoeoneves.fintrack.domain.usecase.ObserveInvestmentHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

// The route argument (InvestmentDetail), read from the SavedStateHandle.
private const val ARG_PERIOD = "period"

// The selected month ("yyyy-MM"), kept across process death.
private const val KEY_MONTH = "month"

/** Months of the chart for a month period. */
const val INVESTMENT_MONTHS = 12

private const val DECEMBER = 12
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** The investments page: its bars, totals, the selected month's split and movements. */
sealed interface InvestmentDetailUiState {
    data object Loading : InvestmentDetailUiState

    data object Error : InvestmentDetailUiState

    /**
     * @property months the bars, oldest first (12 months for a month period, the year's months for a year).
     * @property selected the month whose split and [movements] are shown.
     * @property yearCents invested in [year] up to the last month of the window; [allTimeCents] ever up to then.
     */
    data class Content(
        val months: List<InvestmentMonth>,
        val selectedIndex: Int,
        val year: Int,
        val yearCents: Long,
        val allTimeCents: Long,
        val isCurrentMonth: Boolean,
        val movements: List<Expense>,
    ) : InvestmentDetailUiState {
        val selected: InvestmentMonth get() = months[selectedIndex]
    }
}

/**
 * The investments page (reached from Home's "Invested" row): monthly bars ending at the route's month (or the months
 * of the route's year), the totals, the selected month's split per instrument and its movements.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InvestmentDetailViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        observeInvestmentHistory: ObserveInvestmentHistory,
        private val clock: Clock,
    ) : ViewModel() {
        private val zone: ZoneId = clock.zone
        private val today: YearMonth = currentYearMonth(clock.instant(), zone)
        private val period: Period =
            decodePeriod(savedStateHandle[ARG_PERIOD] ?: Period.CurrentMonth.encode(), clock.instant(), zone)
        private val endMonth: YearMonth = endMonthOf(period)
        private val monthCount: Int = if (period is Period.Year) endMonth.monthValue else INVESTMENT_MONTHS
        private val window: List<YearMonth> = (monthCount - 1 downTo 0).map { endMonth.minusMonths(it.toLong()) }

        private val selectedMonth = savedStateHandle.getStateFlow(KEY_MONTH, endMonth.toString())

        val uiState: StateFlow<InvestmentDetailUiState> =
            combine(observeInvestmentHistory(endMonth, monthCount, zone), selectedMonth) { overview, selected ->
                contentOf(overview, selectedOf(selected))
            }.catch { emit(InvestmentDetailUiState.Error) }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    InvestmentDetailUiState.Loading,
                )

        /** Selects [month] (one of the chart's months; anything else is ignored). */
        fun onSelectMonth(month: YearMonth) {
            if (month in window) savedStateHandle[KEY_MONTH] = month.toString()
        }

        private fun endMonthOf(period: Period): YearMonth =
            when (period) {
                is Period.Year -> if (period.year >= today.year) today else YearMonth.of(period.year, DECEMBER)
                else -> period.anchorMonth(clock.instant(), zone) ?: today
            }

        private fun selectedOf(token: String): YearMonth =
            runCatching { YearMonth.parse(token) }.getOrNull()?.takeIf { it in window } ?: endMonth

        private fun contentOf(
            overview: InvestmentOverview,
            selected: YearMonth,
        ): InvestmentDetailUiState {
            val history = overview.history
            val index = window.indexOf(selected).coerceAtLeast(0)
            return InvestmentDetailUiState.Content(
                months = history.months,
                selectedIndex = index,
                year = endMonth.year,
                yearCents = overview.yearCents,
                allTimeCents = overview.allTimeCents,
                isCurrentMonth = selected == today,
                movements = history.movements.filter { YearMonth.from(it.date.atZone(zone)) == selected },
            )
        }
    }
