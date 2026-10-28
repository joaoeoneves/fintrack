package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.amountIn
import com.joaoeoneves.fintrack.domain.model.effectiveWorkingDays
import com.joaoeoneves.fintrack.domain.model.isActiveIn
import com.joaoeoneves.fintrack.domain.model.labelFor
import com.joaoeoneves.fintrack.domain.model.paydayIncome
import com.joaoeoneves.fintrack.domain.model.paydayIncomeId
import com.joaoeoneves.fintrack.domain.model.paydayMonthsDue
import com.joaoeoneves.fintrack.domain.model.paydayOf
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SalaryLineLabelsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** What [RecordPayday.receive] records: the paying [lines] of [month] as received on [date]. */
data class ReceiveRequest(
    val lines: List<PlannedIncome>,
    val month: YearMonth,
    val workingDays: Int,
    val date: LocalDate,
    val today: LocalDate,
    val zone: ZoneId,
    /** Replaces the amount of the single line of [lines]; refused for several lines. */
    val amountOverrideCents: Long? = null,
)

/**
 * Records the planned income as real income: automatically on each month's payday ([runDue]) or when the user
 * marks a row as received ([receive]). Both use the deterministic id `pay_<lineId>_<yyyyMM>`, so repeating or two
 * devices racing never duplicates an income.
 */
class RecordPayday
    @Inject
    constructor(
        private val plannedIncomeRepository: PlannedIncomeRepository,
        private val monthSettingsRepository: MonthSettingsRepository,
        private val incomeRepository: IncomeRepository,
        private val automationRepository: AutomationSettingsRepository,
        private val labelsProvider: SalaryLineLabelsProvider,
    ) {
        /**
         * Records every active line of each month whose payday passed since the stored cursor, oldest first, and
         * moves the cursor after each month. Does nothing while the automation is off or the plan has no lines (a
         * line list that has not loaded yet must not advance the cursor). Returns how many incomes were created;
         * stops at the first failure so the next run resumes there.
         */
        suspend fun runDue(
            today: LocalDate,
            zone: ZoneId,
        ): Int {
            val settings = automationRepository.observeAutomationSettings().first()
            val due =
                if (settings.paydayEnabled) paydayMonthsDue(settings.paydayRecordedThrough, today) else emptyList()
            val lines = if (due.isEmpty()) emptyList() else plannedIncomeRepository.observePlannedIncome().first()
            // A line list that is empty (maybe not loaded yet) records nothing and leaves the cursor alone.
            val months = if (lines.isEmpty()) emptyList() else due
            val monthSettings =
                if (months.isEmpty()) emptyList() else monthSettingsRepository.observeMonthSettings().first()
            var recorded = 0
            for (month in months) {
                val current = monthSettings.firstOrNull { it.month == month }
                val workingDays = effectiveWorkingDays(month, current?.workingDaysOverride, current?.daysOff ?: 0)
                recorded += recordMonth(month, lines, workingDays, zone) ?: break
            }
            return recorded
        }

        /** Records [month]'s missing incomes and moves the cursor; the count created, or `null` on a failure. */
        private suspend fun recordMonth(
            month: YearMonth,
            lines: List<PlannedIncome>,
            workingDays: Int,
            zone: ZoneId,
        ): Int? {
            val labels = labelsProvider.current()
            val incomes =
                lines
                    .filter { it.isActiveIn(month) }
                    .mapNotNull {
                        it.paydayIncome(month, workingDays, paydayOf(month), zone)?.named(it, labels)
                    }
            val missing = mutableListOf<Income>()
            for (income in incomes) {
                val existing = incomeRepository.getIncome(income.id)
                if (existing.isFailure) return null
                if (existing.getOrNull() == null) missing += income
            }
            return missing.size.takeIf { automationRepository.recordPayday(month, missing).isSuccess }
        }

        /**
         * Records the paying lines of [ReceiveRequest.lines] as received on [ReceiveRequest.date]. Refused for a
         * future date, for an amount override on several lines and when nothing pays.
         */
        @Suppress("TooGenericExceptionCaught") // validation and repository failures all surface as a Result
        suspend fun receive(request: ReceiveRequest): Result<Unit> =
            try {
                with(request) {
                    require(date <= today) { "Date $date is in the future" }
                    val labels = labelsProvider.current()
                    val incomes =
                        lines
                            .filter { it.isActiveIn(month) }
                            .mapNotNull {
                                it.paydayIncome(month, workingDays, date, zone, amountFor(it))?.named(it, labels)
                            }
                    require(incomes.isNotEmpty()) { "Nothing to receive" }
                    require(amountOverrideCents == null || incomes.size == 1) { "Override needs a single line" }
                    incomes.forEach { income -> incomeRepository.addIncome(income).getOrThrow() }
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Broad catch: validation and repository failures all surface as a Result for the caller.
                Result.failure(e)
            }

        /**
         * Deletes the payday incomes recorded for [lines] in [month], so the row counts as not received again.
         * Lines without a recorded income are skipped silently; stops at the first failure.
         */
        suspend fun unreceive(
            lines: List<PlannedIncome>,
            month: YearMonth,
        ): Result<Unit> {
            for (line in lines) {
                val result = incomeRepository.deleteIncome(paydayIncomeId(line.id, month))
                if (result.isFailure) return result
            }
            return Result.success(Unit)
        }

        // A generated line is named in the app language; a manual line (or an extra) keeps the user's own text.
        private fun Income.named(
            line: PlannedIncome,
            labels: SalaryLineLabels,
        ): Income = line.managedKey?.let { labels.labelFor(it) }?.let { copy(source = it) } ?: this

        private fun ReceiveRequest.amountFor(l: PlannedIncome) = amountOverrideCents ?: l.amountIn(month, workingDays)
    }
