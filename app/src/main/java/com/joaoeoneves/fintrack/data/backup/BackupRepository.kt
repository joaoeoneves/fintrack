package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.BuildConfig
import com.joaoeoneves.fintrack.data.local.toLocal
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesStore
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.BuiltInCategories
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.domain.repository.SuggestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import javax.inject.Inject

/**
 * Builds backups from the current (session-switched) data and restores them into the guest store or the
 * signed-in account.
 */
@Suppress("LongParameterList") // one collaborator per data source; bundling them would only hide the list
class BackupRepository
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val incomeRepository: IncomeRepository,
        private val planBackupSource: PlanBackupSource,
        private val categoryRepository: CategoryRepository,
        private val preferencesStore: UserPreferencesStore,
        private val restorer: BackupRestorer,
        private val serverSync: ServerSync = LocalOnlyServerSync,
        private val suggestionRepository: SuggestionRepository = NoDismissedSuggestions,
    ) {
        /** Snapshot of the current mode's data plus preferences; works signed in and as a guest. */
        suspend fun buildBackup(exportedAt: Instant): Result<BackupDocument> =
            runCatchingCancellable {
                // A signed-in export never uses cache or error-fallback data: read the server first or fail.
                serverSync.refresh().getOrThrow()
                val expenses = expenseRepository.getAllExpenses().getOrThrow()
                val income = incomeRepository.getAllIncome().getOrThrow()
                val plan = planBackupSource.read()
                val budgets = plan.budgets
                val categories = categoryRepository.observeCategories().first()
                val prefs = preferencesStore.data.first()
                val stored = categories.filter { it.needsStoring() }.map { it.toLocal() }
                val localExpenses = expenses.map { it.toLocal() }
                val localBudgets = budgets.map { it.toLocal() }
                val localRecurring = plan.recurring.map { it.toLocal() }
                val dismissed = suggestionRepository.observeDismissedRecurring().first().sorted()
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
                    goals = plan.goals.map { it.toLocal() },
                    automation = plan.automation.takeIf { it != AutomationSettings() }?.toLocal(),
                    dismissedRecurringSuggestions = dismissed,
                    categories =
                        withPlaceholderCategories(
                            categories = stored,
                            expenses = localExpenses,
                            budgets = localBudgets,
                            recurring = localRecurring,
                            maxOrder = maxOrder,
                        ).namingPlaceholders(stored.size, plan.deletedCategoryName),
                )
            }

        /** Whether a restore would replace existing data (an account always has some to replace). */
        suspend fun localHasData(): Boolean = restorer.hasData()

        /**
         * Replaces the current data (guest store or signed-in account, by session) with [document] and applies its
         * preferences (unknown preference names keep the current value). [onProgress] reports account progress.
         */
        suspend fun restore(
            document: BackupDocument,
            onProgress: (written: Int, total: Int) -> Unit = { _, _ -> },
        ): AccountRestoreResult = restorer.restore(document, onProgress)

        /** User categories and changed built-ins are stored; untouched built-ins and placeholders are not. */
        private fun Category.needsStoring(): Boolean = !isUnknown && (!builtIn || this != BuiltInCategories.byId(id))
    }

/** No server to read: the default for guest-only use and tests. */
private object LocalOnlyServerSync : ServerSync {
    override suspend fun refresh(): Result<Unit> = Result.success(Unit)
}

/** No dismissed suggestions: the default for tests that do not care. */
private object NoDismissedSuggestions : SuggestionRepository {
    override fun observeDismissedRecurring(): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun dismissRecurring(key: String): Result<Unit> = Result.success(Unit)

    override suspend fun undismissRecurring(key: String): Result<Unit> = Result.success(Unit)
}
