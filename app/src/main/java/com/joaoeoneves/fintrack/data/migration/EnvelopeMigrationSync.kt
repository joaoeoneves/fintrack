package com.joaoeoneves.fintrack.data.migration

import android.content.Context
import android.util.Log
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.EnvelopeMigration
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.envelopeMigrationOf
import com.joaoeoneves.fintrack.domain.repository.BudgetRepository
import com.joaoeoneves.fintrack.domain.repository.CategoryRepository
import com.joaoeoneves.fintrack.domain.repository.EnvelopeRepository
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository
import com.joaoeoneves.fintrack.ui.common.builtInNameRes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "EnvelopeMigrationSync"

/**
 * Moves a pre-envelope plan to envelopes without the user doing anything: groups stay envelopes, every budgeted
 * category outside one gets an envelope of its own and income-line earmarks become the envelopes' funding line
 * (see [envelopeMigrationOf]). It runs while there is something to migrate, so it is a no-op once migrated, and an
 * identical set of writes is attempted once per process so a failing write is not retried in a loop.
 */
@Singleton
class EnvelopeMigrationSync
    @Inject
    constructor(
        private val budgets: BudgetRepository,
        private val categories: CategoryRepository,
        private val envelopes: EnvelopeRepository,
        private val plannedIncome: PlannedIncomeRepository,
        @ApplicationContext private val context: Context,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val started = AtomicBoolean(false)
        private var lastAttempted: EnvelopeMigration? = null

        fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                combine(
                    budgets.observeBudgets(),
                    categories.observeCategories(),
                    envelopes.observeEnvelopes(),
                    plannedIncome.observePlannedIncome(),
                    ::PlanSnapshot,
                ).catch { Log.w(TAG, "Failed to observe the plan", it) }
                    .collectLatest { snapshot ->
                        val migration = migrationOf(snapshot)
                        if (migration.isEmpty || migration == lastAttempted) return@collectLatest
                        lastAttempted = migration
                        migration.dropped.forEach { Log.i(TAG, "Earmark of line $it could not become a funding line") }
                        apply(migration)
                    }
            }
        }

        private fun migrationOf(snapshot: PlanSnapshot): EnvelopeMigration =
            envelopeMigrationOf(
                budgets = snapshot.budgets,
                categories = snapshot.categories,
                envelopes = snapshot.envelopes,
                lines = snapshot.lines,
                nameOf = ::nameOf,
            )

        private fun nameOf(category: Category): String =
            category.customName
                ?: builtInNameRes[category.id]?.let { context.getString(it) }
                ?: category.id.value

        // Envelopes first (they file the categories), then the lines; stops at the first failure.
        @Suppress("TooGenericExceptionCaught") // Intentional: a migration must never crash the app.
        private suspend fun apply(migration: EnvelopeMigration) {
            try {
                migration.envelopes.forEach { envelopes.saveEnvelope(it).getOrThrow() }
                migration.lines.forEach { plannedIncome.savePlannedIncome(it).getOrThrow() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to migrate the plan to envelopes", e)
            }
        }
    }

private class PlanSnapshot(
    val budgets: List<Budget>,
    val categories: List<Category>,
    val envelopes: List<Envelope>,
    val lines: List<PlannedIncome>,
)
