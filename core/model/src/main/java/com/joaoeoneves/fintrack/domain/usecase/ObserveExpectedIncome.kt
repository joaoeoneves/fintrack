package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.ExpectedIncome
import com.joaoeoneves.fintrack.domain.model.ExpectedIncomeEntry
import com.joaoeoneves.fintrack.domain.model.IncomeFunding
import com.joaoeoneves.fintrack.domain.model.PAYDAY_LEAD_DAYS
import com.joaoeoneves.fintrack.domain.model.effectiveWorkingDays
import com.joaoeoneves.fintrack.domain.model.incomeRowsIn
import com.joaoeoneves.fintrack.domain.model.payDateIn
import com.joaoeoneves.fintrack.domain.model.paydayOf
import com.joaoeoneves.fintrack.domain.model.receivedIncomes
import com.joaoeoneves.fintrack.domain.model.salaryShiftEnabled
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.NoPlanSettings
import com.joaoeoneves.fintrack.domain.repository.PlanSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * The planned income not yet received that is payable this calendar month or earlier, for the dashboard: nothing
 * before [PAYDAY_LEAD_DAYS] days ahead of its pay date, then every paying row without its payday income. With the
 * salary paying the next month this includes the salary of the month's end (it funds next month's plan) and last
 * month's salary when it was never recorded; each [ExpectedIncomeEntry] says which plan month it belongs to.
 */
class ObserveExpectedIncome
    @Inject
    constructor(
        private val plannedIncomeRepository: PlannedIncomeRepository,
        private val monthSettingsRepository: MonthSettingsRepository,
        private val incomeRepository: IncomeRepository,
        private val planSettings: PlanSettingsRepository = NoPlanSettings,
    ) {
        operator fun invoke(
            today: LocalDate,
            zone: ZoneId,
        ): Flow<ExpectedIncome> {
            val month = YearMonth.from(today)
            val start =
                month
                    .minusMonths(1)
                    .atDay(1)
                    .atStartOfDay(zone)
                    .toInstant()
            val end =
                month
                    .plusMonths(1)
                    .atDay(1)
                    .atStartOfDay(zone)
                    .toInstant()
            return combine(
                combine(
                    plannedIncomeRepository.observePlannedIncome(),
                    monthSettingsRepository.observeMonthSettings(),
                    planSettings.observeSalaryPaysNextMonth(),
                ) { lines, settings, shift -> Triple(lines, settings, shift) },
                incomeRepository.observeIncome(start, end),
            ) { (lines, monthSettings, shift), incomes ->
                val funding = IncomeFunding.of(salaryShiftEnabled(shift, lines), monthSettings)
                val entries =
                    listOf(month, month.plusMonths(1)).flatMap { plan ->
                        incomeRowsIn(lines, plan, funding)
                            .filter { it.cents > 0L && it.receivedIncomes(plan, funding, incomes) == null }
                            .map { ExpectedIncomeEntry(it, plan, it.payDateIn(plan, funding)) }
                            .filter { YearMonth.from(it.payDate) <= month }
                            .filter { today >= it.payDate.minusDays(PAYDAY_LEAD_DAYS) }
                    }
                val current = monthSettings.firstOrNull { it.month == month }
                val workingDays = effectiveWorkingDays(month, current?.workingDaysOverride, current?.daysOff ?: 0)
                ExpectedIncome(
                    month = month,
                    workingDays = workingDays,
                    payday = entries.minOfOrNull { it.payDate } ?: paydayOf(month),
                    rows = entries.map { it.row },
                    entries = entries,
                    funding = funding,
                )
            }
        }
    }
