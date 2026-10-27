package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.BuildConfig
import com.joaoeoneves.fintrack.data.local.toLocal
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesStore
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * Builds backups from the current (session-switched) data and restores them into the guest store.
 */
class BackupRepository
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val incomeRepository: IncomeRepository,
        private val planBackupSource: PlanBackupSource,
        private val categoryRepository: CategoryRepository,
        private val preferencesStore: UserPreferencesStore,
        private val restorer: GuestBackupRestorer,
    ) {
        /** Snapshot of the current mode's data plus preferences; works signed in and as a guest. */
        suspend fun buildBackup(exportedAt: Instant): Result<BackupDocument> =
            runCatchingCancellable {
                val expenses = expenseRepository.getAllExpenses().getOrThrow()
                val income = incomeRepository.getAllIncome().getOrThrow()
                val plan = planBackupSource.read()
                val budgets = plan.budgets
                val categories = categoryRepository.observeCategories().first()
                val prefs = preferencesStore.data.first()
                val localExpenses = expenses.map { it.toLocal() }
                val localBudgets = budgets.map { it.toLocal() }
                val localRecurring = plan.recurring.map { it.toLocal() }
                val maxOrder = categories.map { it.order }.filter { it != Int.MAX_VALUE }.maxOrNull() ?: 0
                BackupDocument(
                    exportedAt = exportedAt.toString(),
                    appVersion = BuildConfig.VERSION_NAME,
                    preferences =
                        BackupPreferences(
                            theme = prefs.theme.name,
                            currency = prefs.currency.name,
                            language = prefs.language.name,
                        ),
                    expenses = localExpenses,
                    income = income.map { it.toLocal() },
                    budgets = localBudgets,
                    plannedIncome = plan.plannedIncome.map { it.toLocal() },
                    recurring = localRecurring,
                    monthSettings = plan.monthSettings.filterNot { it.isBlank }.map { it.toLocal() },
                    categoryGroups = plan.categoryGroups.map { it.toLocal() },
                    salaryProfile = plan.salaryProfile?.toLocal(),
                    categories =
                        withPlaceholderCategories(
                            categories = categories.filter { it.needsStoring() }.map { it.toLocal() },
                            expenses = localExpenses,
                            budgets = localBudgets,
                            recurring = localRecurring,
                            maxOrder = maxOrder,
                        ),
                )
            }

        /** Whether the guest store currently holds any data (a restore would replace it). */
        suspend fun localHasData(): Boolean = restorer.localHasData()

        /**
         * Replaces the guest data with [document] and applies its preferences (unknown preference
         * names keep the current value). Fails unless the session is a guest.
         */
        suspend fun restoreAsGuest(document: BackupDocument): Result<Unit> = restorer.restore(document)

        /** User categories and changed built-ins are stored; untouched built-ins and placeholders are not. */
        private fun Category.needsStoring(): Boolean = !isUnknown && (!builtIn || this != BuiltInCategories.byId(id))
    }
