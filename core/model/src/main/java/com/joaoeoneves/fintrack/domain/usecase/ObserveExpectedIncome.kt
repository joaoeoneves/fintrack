package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.ExpectedIncome
import com.joaoeoneves.fintrack.domain.model.PAYDAY_LEAD_DAYS
import com.joaoeoneves.fintrack.domain.model.effectiveWorkingDays
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
 * The current month's planned income not yet received, for the dashboard: nothing before [PAYDAY_LEAD_DAYS] days
 * ahead of payday, then every paying row without its payday income until the month ends.
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
            val start = month.atDay(1).atStartOfDay(zone).toInstant()
            val end =
                month
                    .plusMonths(1)
                    .atDay(1)
                    .atStartOfDay(zone)
                    .toInstant()
            return combine(
                plannedIncomeRepository.observePlannedIncome(),
                monthSettingsRepository.observeMonthSettings(),
                incomeRepository.observeIncome(start, end),
            ) { lines, monthSettings, incomes ->
                val current = monthSettings.firstOrNull { it.month == month }
                val workingDays = effectiveWorkingDays(month, current?.workingDaysOverride, current?.daysOff ?: 0)
                val rows =
                    incomeRowsIn(lines, month, workingDays).filter {
                        it.cents > 0L &&
                            today >= it.payDateIn(month).minusDays(PAYDAY_LEAD_DAYS) &&
                            it.receivedIncomes(month, workingDays, incomes) == null
                    }
                ExpectedIncome(month, workingDays, rows.minOfOrNull { it.payDateIn(month) } ?: paydayOf(month), rows)
            }
        }
    }
