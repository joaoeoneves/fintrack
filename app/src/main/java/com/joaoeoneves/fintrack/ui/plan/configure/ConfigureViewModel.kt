package com.joaoeoneves.fintrack.ui.plan.configure

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.IncomeFunding
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.RecurringType
import com.joaoeoneves.fintrack.domain.model.annualizedCents
import com.joaoeoneves.fintrack.domain.model.asOf
import com.joaoeoneves.fintrack.domain.model.billsOf
import com.joaoeoneves.fintrack.domain.model.effectiveWorkingDays
import com.joaoeoneves.fintrack.domain.model.isEnded
import com.joaoeoneves.fintrack.domain.model.isExtra
import com.joaoeoneves.fintrack.domain.model.isManaged
import com.joaoeoneves.fintrack.domain.model.monthlyEquivalentCents
import com.joaoeoneves.fintrack.domain.model.salaryShiftEnabled
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import com.joaoeoneves.fintrack.domain.repository.PlanSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.RecurringRepository
import com.joaoeoneves.fintrack.ui.plan.ConfigureStatus
import com.joaoeoneves.fintrack.ui.plan.PlanRepositories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

private const val TAG = "ConfigureViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** The global set-up status (income, envelopes, bills, categories), measured on the current month. */
@HiltViewModel
class ConfigureViewModel
    @Inject
    constructor(
        repositories: PlanRepositories,
        envelopes: EnvelopeRepository,
        recurring: RecurringRepository,
        private val planSettings: PlanSettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        /** The "salary pays the next month" switch: `null` (hidden) without a salary profile, else its state. */
        val salaryPaysNextMonth: StateFlow<Boolean?> =
            combine(planSettings.observeSalaryPaysNextMonth(), repositories.plannedIncome.observePlannedIncome()) {
                pref,
                lines,
                ->
                if (lines.any { it.isManaged }) salaryShiftEnabled(pref, lines) else null
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), null)

        fun onSalaryPaysNextMonthChanged(enabled: Boolean) {
            viewModelScope.launch { planSettings.setSalaryPaysNextMonth(enabled) }
        }

        /** Empty (every row reads "not set") until the first emission. */
        val status: StateFlow<ConfigureStatus> =
            statusFlow(repositories, envelopes.observeEnvelopes(), recurring.observeRecurring())
                .catch { Log.w(TAG, "Failed to observe the set-up status", it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), EMPTY_STATUS)

        private fun statusFlow(
            repositories: PlanRepositories,
            envelopes: Flow<List<Envelope>>,
            recurring: Flow<List<RecurringItem>>,
        ): Flow<ConfigureStatus> =
            combine(
                repositories.plannedIncome.observePlannedIncome(),
                repositories.categories.observeCategories(),
                repositories.monthSettings.observeMonthSettings(),
                repositories.goals.observeGoals(),
                combine(envelopes, recurring, ::Pair),
            ) { lines, categories, settings, goals, (envelopeList, recurringList) ->
                val month = YearMonth.now(clock)
                val monthSettings = settings.firstOrNull { it.month == month }
                val days = effectiveWorkingDays(month, monthSettings?.workingDaysOverride, monthSettings?.daysOff ?: 0)
                // A savings plan's monthly item is the plan's, not a bill.
                val bills = billsOf(recurringList, goals).filter { it.active && !it.isEnded(LocalDate.now(clock)) }
                val funding = IncomeFunding.unshifted(month, days)
                val regular = lines.asOf(month).filterNot { it.isExtra }
                ConfigureStatus(
                    incomeMonthlyCents = regular.sumOf { funding.amountIn(it, month) },
                    incomeLineCount = regular.size,
                    envelopeCount = envelopeList.asOf(month).size,
                    billCount = bills.size,
                    billsMonthlyCents =
                        monthlyEquivalentCents(annualizedCents(bills, RecurringType.EXPENSE, LocalDate.now(clock))),
                    categoryCount = categories.count { !it.archived },
                )
            }
    }

private val EMPTY_STATUS =
    ConfigureStatus(
        incomeMonthlyCents = 0,
        incomeLineCount = 0,
        envelopeCount = 0,
        billCount = 0,
        billsMonthlyCents = 0,
        categoryCount = 0,
    )
