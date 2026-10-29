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
import kotlinx.coroutines.flow.first
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
    ) {
        /** Snapshot of the current mode's data plus preferences; works signed in and as a guest. */
        suspend fun buildBackup(exportedAt: Instant): Result<BackupDocument> =
            runCatchingCancellable {
                // A signed-in export never uses cache or error-fallback data: read the server first or fail.
                // The server read feeds the export directly (no second read); a guest reads its local repositories.
                val server = serverSync.readAll().getOrThrow()
                val expenses = server?.expenses ?: expenseRepository.getAllExpenses().getOrThrow()
                val income = server?.income ?: incomeRepository.getAllIncome().getOrThrow()
                val plan = server?.let(::planFrom) ?: planBackupSource.read()
                val budgets = plan.budgets
                val categories = server?.categories ?: categoryRepository.observeCategories().first()
                val prefs = preferencesStore.data.first()
                val stored = categories.filter { it.needsStoring() }.map { it.toLocal() }
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
                    goals = plan.goals.map { it.toLocal() },
                    automation = plan.automation.takeIf { it != AutomationSettings() }?.toLocal(),
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

        private fun planFrom(server: ServerData) =
            PlanBackupData(
                budgets = server.budgets,
                plannedIncome = server.plannedIncome,
                recurring = server.recurring,
                monthSettings = server.monthSettings,
                categoryGroups = server.categoryGroups,
                salaryProfile = server.salaryProfile,
                goals = server.goals,
                automation = server.automation,
                deletedCategoryName = planBackupSource.deletedCategoryName(),
            )

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
