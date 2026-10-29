package com.joaoeoneves.fintrack.data.backup

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.joaoeoneves.fintrack.data.automationSettingsDoc
import com.joaoeoneves.fintrack.data.awaitWithin
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.data.salaryProfileDoc
import com.joaoeoneves.fintrack.data.toAutomationSettings
import com.joaoeoneves.fintrack.data.toBudgets
import com.joaoeoneves.fintrack.data.toCategoryOrNull
import com.joaoeoneves.fintrack.data.toEnvelopeList
import com.joaoeoneves.fintrack.data.toExpenses
import com.joaoeoneves.fintrack.data.toGoalList
import com.joaoeoneves.fintrack.data.toIncomeList
import com.joaoeoneves.fintrack.data.toMonthSettings
import com.joaoeoneves.fintrack.data.toPlannedIncomeList
import com.joaoeoneves.fintrack.data.toRecurringList
import com.joaoeoneves.fintrack.data.toSalaryProfileOrNull
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Budget
import com.joaoeoneves.fintrack.domain.model.Category
import com.joaoeoneves.fintrack.domain.model.Envelope
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.model.RecurringItem
import com.joaoeoneves.fintrack.domain.model.SalaryProfile
import com.joaoeoneves.fintrack.domain.model.SavingsGoal
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.mergeCategories
import com.joaoeoneves.fintrack.domain.model.resolvedWith
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** The server of the signed-in account could not be read, so an export would not be the real data. */
class ServerUnreachableException(
    cause: Throwable,
) : Exception("The account's server could not be reached", cause)

// A plain holder of one export's data; splitting it would only scatter the fields.

/** Everything an account export needs, as read from the server in one go (already mapped to domain models). */
@Suppress("LongParameterList")
class ServerData(
    val expenses: List<Expense>,
    val income: List<Income>,
    val budgets: List<Budget>,
    val plannedIncome: List<PlannedIncome>,
    val recurring: List<RecurringItem>,
    val monthSettings: List<MonthSettings>,
    val categoryGroups: List<Envelope>,
    val goals: List<SavingsGoal>,
    val categories: List<Category>,
    val salaryProfile: SalaryProfile?,
    val automation: AutomationSettings,
)

/** Reads the server's state for an export, so it is never a stale cache. */
interface ServerSync {
    /** Succeeds at once for a guest; for an account reads every collection from the server or fails. */
    suspend fun refresh(): Result<Unit>

    /**
     * The server's data read once (all collections at the same time), or `null` when there is no server to read
     * (a guest, or a [ServerSync] that only [refresh]es): the caller then reads its repositories. Fails with
     * [ServerUnreachableException] when the server cannot be read.
     */
    suspend fun readAll(): Result<ServerData?> = refresh().map { null }
}

/**
 * Reads every account collection and both singleton documents with [Source.SERVER], all at once under one overall
 * timeout, and maps the results to domain models with the repositories' own mappers, so an export is built from
 * that single read. An unreachable server (offline, timeout, permission) fails with [ServerUnreachableException]. A
 * guest (by the app session, not the auth user) never touches the server.
 */
class FirestoreServerSync
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val firebaseAuth: FirebaseAuth,
        private val sessionRepository: SessionRepository,
    ) : ServerSync {
        override suspend fun refresh(): Result<Unit> = readAll().map { }

        override suspend fun readAll(): Result<ServerData?> {
            val uid =
                firebaseAuth.currentUser?.uid?.takeIf { sessionRepository.session.value is Session.SignedIn }
                    ?: return Result.success(null)
            return runCatchingCancellable {
                val user = firestore.collection("users").document(uid)
                val collections: Map<String, Task<QuerySnapshot>> =
                    RESTORE_PRUNE_ORDER.associateWith { user.collection(it).get(Source.SERVER) }
                val profile = salaryProfileDoc(firestore, uid).get(Source.SERVER)
                val automation = automationSettingsDoc(firestore, uid).get(Source.SERVER)
                val (snapshots, profileDoc, automationDoc) =
                    awaitWithin(OVERALL_TIMEOUT) {
                        Triple(collections.mapValues { it.value.await() }, profile.await(), automation.await())
                    }
                val categories =
                    mergeCategories(snapshots.getValue("categories").documents.mapNotNull { it.toCategoryOrNull() })
                ServerData(
                    expenses = snapshots.getValue("expenses").toExpenses().resolvedWith(categories),
                    income = snapshots.getValue("income").toIncomeList(),
                    budgets = snapshots.getValue("budgets").toBudgets(),
                    plannedIncome = snapshots.getValue("plannedIncome").toPlannedIncomeList(),
                    recurring = snapshots.getValue("recurring").toRecurringList(),
                    monthSettings = snapshots.getValue("monthSettings").toMonthSettings(),
                    categoryGroups = snapshots.getValue("categoryGroups").toEnvelopeList(),
                    goals = snapshots.getValue("goals").toGoalList(),
                    categories = categories,
                    salaryProfile = profileDoc.takeIf { it.exists() }?.toSalaryProfileOrNull(),
                    automation = automationDoc.toAutomationSettings(),
                )
            }.recoverCatching { throw ServerUnreachableException(it) }
        }
    }

private val OVERALL_TIMEOUT = 45.seconds
