package com.joaoeoneves.fintrack.ui.dashboard.quickadd

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaoeoneves.fintrack.domain.model.QuickAddSuggestion
import com.joaoeoneves.fintrack.domain.model.quickAddSuggestions
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject

private const val TAG = "QuickAddViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** How many days back the repeated expenses are looked for (matches the domain function's window). */
private const val WINDOW_DAYS = 90L

/** Home's "Quick add" chips: the expenses the user repeats, each a one-tap prefill of the new-expense form. */
@HiltViewModel
class QuickAddViewModel
    @Inject
    constructor(
        expenseRepository: ExpenseRepository,
        private val clock: Clock,
    ) : ViewModel() {
        /** The chips to show, most frequent first; empty hides the row. */
        val suggestions: StateFlow<List<QuickAddSuggestion>> =
            run {
                val zone = clock.zone
                val now = ZonedDateTime.now(clock)
                val from =
                    now
                        .toLocalDate()
                        .minusDays(WINDOW_DAYS)
                        .atStartOfDay(zone)
                        .toInstant()
                expenseRepository
                    .observeExpenses(from, now.plusDays(1).toInstant())
                    .map { quickAddSuggestions(it, now.toLocalDate(), zone) }
            }.catch { e ->
                Log.w(TAG, "Quick-add flow failed", e)
                emit(emptyList())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())
    }
