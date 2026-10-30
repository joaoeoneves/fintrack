package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.ExpectedIncome
import com.joaoeoneves.fintrack.domain.model.ExpectedIncomeEntry
import com.joaoeoneves.fintrack.domain.model.IncomeMonth
import com.joaoeoneves.fintrack.domain.model.PAYDAY_LEAD_DAYS
import com.joaoeoneves.fintrack.domain.model.effectiveWorkingDays
import com.joaoeoneves.fintrack.domain.model.filterOwnedInMonth
import com.joaoeoneves.fintrack.domain.model.incomeRowsIn
import com.joaoeoneves.fintrack.domain.model.payDateIn
import com.joaoeoneves.fintrack.domain.model.paydayOf
import com.joaoeoneves.fintrack.domain.model.receivedIncomes
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * The planned income not yet received that is payable this calendar month or earlier, for the dashboard: nothing
 * before [PAYDAY_LEAD_DAYS] days ahead of its pay date, then every paying row without its payday income. Covers
 * the months [month - 1, month]: last month's unrecorded lines stay under "A receber", next month's never show up;
 * each [ExpectedIncomeEntry] says which month it is paid in.
 */
class ObserveExpectedIncome
    @Inject
    constructor(
        private val plannedIncomeRepository: PlannedIncomeRepository,
        private val monthSettingsRepository: MonthSettingsRepository,
        private val incomeRepository: IncomeRepository,
    ) {
        operator fun invoke(
            today: LocalDate,
            zone: ZoneId,
        ): Flow<ExpectedIncome> {
            val month = YearMonth.from(today)
            // Receipts are looked up across a month wider on each side: a payday income dated outside its own
            // month (an early pay date, a late tick) still counts for the month its id names.
            val start =
                month
                    .minusMonths(2)
                    .atDay(1)
                    .atStartOfDay(zone)
                    .toInstant()
            val end =
                month
                    .plusMonths(2)
                    .atDay(1)
                    .atStartOfDay(zone)
                    .toInstant()
            return combine(
                plannedIncomeRepository.observePlannedIncome(),
                monthSettingsRepository.observeMonthSettings(),
                incomeRepository.observeIncome(start, end),
            ) { lines, monthSettings, incomes ->
                val incomeMonth = IncomeMonth.of(monthSettings)
                val entries =
                    listOf(month.minusMonths(1), month).flatMap { payMonth ->
                        val owned = filterOwnedInMonth(incomes, payMonth, zone)
                        incomeRowsIn(lines, payMonth, incomeMonth)
                            .filter { row ->
                                row.cents > 0L && row.receivedIncomes(payMonth, incomeMonth, owned, lines) == null
                            }.map { ExpectedIncomeEntry(it, payMonth, it.payDateIn(payMonth, incomeMonth)) }
                            .filter { today >= it.payDate.minusDays(PAYDAY_LEAD_DAYS) }
                    }
                val current = monthSettings.firstOrNull { it.month == month }
                val workingDays = effectiveWorkingDays(month, current?.daysOff ?: 0)
                ExpectedIncome(
                    month = month,
                    workingDays = workingDays,
                    payday = entries.minOfOrNull { it.payDate } ?: paydayOf(month),
                    rows = entries.map { it.row },
                    entries = entries,
                    incomeMonth = incomeMonth,
                )
            }
        }
    }
