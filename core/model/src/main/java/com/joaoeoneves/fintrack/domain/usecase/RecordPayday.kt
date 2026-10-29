package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.IncomeMonth
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.SalaryLineLabels
import com.joaoeoneves.fintrack.domain.model.amountIn
import com.joaoeoneves.fintrack.domain.model.asOf
import com.joaoeoneves.fintrack.domain.model.effectiveWorkingDays
import com.joaoeoneves.fintrack.domain.model.initialLineCursor
import com.joaoeoneves.fintrack.domain.model.isActiveIn
import com.joaoeoneves.fintrack.domain.model.labelFor
import com.joaoeoneves.fintrack.domain.model.legacyRecurringIncome
import com.joaoeoneves.fintrack.domain.model.lineMonthsDue
import com.joaoeoneves.fintrack.domain.model.payDateIn
import com.joaoeoneves.fintrack.domain.model.paydayIncome
import com.joaoeoneves.fintrack.domain.model.paydayIncomeId
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
 * Records the planned income as real income: automatically on each line's pay date ([runDue]) or when the user
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
         * Records, for every line with automatic recording on, each month whose pay date passed since the line's
         * cursor, oldest first, moving the cursors after each month. A line without a cursor only gets one (this
         * month or last, see [initialLineCursor]) and records nothing: turning it on never backfills. Does nothing
         * for an empty line list (maybe not loaded yet). Returns how many incomes were created; stops at the first
         * failure so the next run resumes there.
         */
        suspend fun runDue(
            today: LocalDate,
            zone: ZoneId,
        ): Int {
            val lines = plannedIncomeRepository.observePlannedIncome().first()
            val cursors = automationRepository.observeAutomationSettings().first().lineRecordedThrough
            val autoLines = lines.filter { it.autoRecord }
            val fresh = autoLines.filter { it.id !in cursors }.associate { it.id to initialLineCursor(it, today) }
            val stale = cursors.keys - autoLines.mapTo(mutableSetOf()) { it.id }
            // An empty line list (maybe not loaded yet) leaves the cursors alone.
            val synced =
                lines.isEmpty() ||
                    (fresh.isEmpty() && stale.isEmpty()) ||
                    automationRepository.updateLineCursors(fresh, stale).isSuccess
            if (!synced || lines.isEmpty()) return 0
            return recordDue(autoLines, cursors, today, zone)
        }

        private suspend fun recordDue(
            autoLines: List<PlannedIncome>,
            cursors: Map<String, YearMonth>,
            today: LocalDate,
            zone: ZoneId,
        ): Int {
            val due = autoLines.filter { it.id in cursors }.associateWith { lineMonthsDue(it, cursors[it.id], today) }
            val months = due.values.flatten().toSortedSet()
            val monthSettings =
                if (months.isEmpty()) emptyList() else monthSettingsRepository.observeMonthSettings().first()
            var recorded = 0
            for (month in months) {
                val current = monthSettings.firstOrNull { it.month == month }
                val workingDays = effectiveWorkingDays(month, current?.workingDaysOverride, current?.daysOff ?: 0)
                val monthLines = due.filterValues { month in it }.keys.toList()
                recorded += recordMonth(month, monthLines, IncomeMonth.forMonth(month, workingDays), zone) ?: break
            }
            return recorded
        }

        /** Records [month]'s missing incomes of [lines] and moves their cursors; the count created, or `null`. */
        private suspend fun recordMonth(
            month: YearMonth,
            lines: List<PlannedIncome>,
            incomeMonth: IncomeMonth,
            zone: ZoneId,
        ): Int? {
            val labels = labelsProvider.current()
            val existing = existingIncomes(month, zone) ?: return null
            val missing =
                lines
                    .filter { it.isActiveIn(month) && it.asOf(month) != null }
                    .filter { line -> existing.none { it.id == paydayIncomeId(line.id, month) } }
                    .filter { line -> legacyRecurringIncome(line, month, existing) == null }
                    .mapNotNull {
                        val amount = incomeMonth.amountIn(it, month)
                        val days = incomeMonth.workingDays(month)
                        it.paydayIncome(month, days, it.payDateIn(month), zone, amount)?.named(it, labels)
                    }
            val cursors = lines.associate { it.id to month }
            return missing.size.takeIf { automationRepository.recordPayday(missing, cursors).isSuccess }
        }

        private suspend fun existingIncomes(
            month: YearMonth,
            zone: ZoneId,
        ): List<Income>? =
            runCatching {
                val start = month.atDay(1).atStartOfDay(zone).toInstant()
                val end =
                    month
                        .plusMonths(1)
                        .atDay(1)
                        .atStartOfDay(zone)
                        .toInstant()
                incomeRepository.observeIncome(start, end).first()
            }.getOrNull()

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
                            .filter { it.isActiveIn(month) && it.asOf(month) != null }
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
         * Deletes the incomes recorded for [lines] in [month] (the payday ones and, for a line migrated from a
         * recurring income, the old `rec_` ones), so the row counts as not received again. Lines without a recorded
         * income are skipped silently; stops at the first failure.
         */
        suspend fun unreceive(
            lines: List<PlannedIncome>,
            month: YearMonth,
            zone: ZoneId = ZoneId.systemDefault(),
        ): Result<Unit> {
            val existing = existingIncomes(month, zone).orEmpty()
            for (line in lines) {
                val ids =
                    listOfNotNull(
                        paydayIncomeId(line.id, month),
                        legacyRecurringIncome(line, month, existing)?.id,
                    )
                for (id in ids) {
                    val result = incomeRepository.deleteIncome(id)
                    if (result.isFailure) return result
                }
            }
            return Result.success(Unit)
        }

        // A generated line is named in the app language; a manual line (or an extra) keeps the user's own text.
        private fun Income.named(
            line: PlannedIncome,
            labels: SalaryLineLabels,
        ): Income = line.managedKey?.let { labels.labelFor(it) }?.let { copy(source = it) } ?: this

        private fun ReceiveRequest.amountFor(l: PlannedIncome) = amountOverrideCents ?: (l.asOf(month)?.amountIn(month, workingDays) ?: 0L)
    }
