package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeCategoryRepository
import com.joaoeoneves.fintrack.data.FakeEnvelopeRepository
import com.joaoeoneves.fintrack.data.FakeExpenseRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlaceholderNames
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeRecurringRepository
import com.joaoeoneves.fintrack.data.FakeSalaryProfileRepository
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Expense
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.repository.ExpenseRepository
import com.joaoeoneves.fintrack.domain.repository.IncomeRepository
import com.joaoeoneves.fintrack.testutil.testExpense
import com.joaoeoneves.fintrack.testutil.testIncome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The export is built from the one server read when there is one, else from the repositories. */
class BackupRepositoryServerDataTest : BackupRepositoryTestBase() {
    private val at = Instant.parse("2026-10-29T10:00:00Z")
    private val day = Instant.parse("2026-10-10T12:00:00Z")

    private class ScriptedSync(
        private val result: Result<ServerData?>,
    ) : ServerSync {
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)

        override suspend fun readAll(): Result<ServerData?> = result
    }

    private fun serverData(
        expenses: List<Expense> = emptyList(),
        income: List<Income> = emptyList(),
        planned: List<PlannedIncome> = emptyList(),
    ) = ServerData(
        expenses = expenses,
        income = income,
        plannedIncome = planned,
        recurring = emptyList(),
        monthSettings = emptyList(),
        categoryGroups = emptyList(),
        categories = emptyList(),
        salaryProfile = null,
        automation = AutomationSettings(),
    )

    private fun backupWith(
        sync: ServerSync,
        expenses: ExpenseRepository = FakeExpenseRepository(listOf(testExpense("local-e", 100, day))),
        incomes: IncomeRepository = FakeIncomeRepository(listOf(testIncome("local-i", 100, day))),
        plannedIncome: List<PlannedIncome> = listOf(PlannedIncome("local-p", "Local", 1L, 0)),
    ) = BackupRepository(
        expenses,
        incomes,
        PlanBackupSource(
            FakePlannedIncomeRepository(plannedIncome),
            FakeRecurringRepository(),
            FakeMonthSettingsRepository(),
            FakeEnvelopeRepository(),
            PlanSingletonSources(
                FakeSalaryProfileRepository(),
                FakeAutomationSettingsRepository(),
                FakePlaceholderNames(),
            ),
        ),
        FakeCategoryRepository(),
        prefsStore,
        restorer,
        sync,
    )

    @Test
    fun serverData_isExported_withoutReadingTheRepositories() =
        runTest {
            val explode = IllegalStateException("repository must not be read")
            val untouchable =
                object : ExpenseRepository by FakeExpenseRepository() {
                    override suspend fun getAllExpenses(): Result<List<Expense>> = Result.failure(explode)
                }
            val data =
                serverData(
                    expenses = listOf(testExpense("srv-e", 500, day)),
                    income = listOf(testIncome("srv-i", 900, day)),
                    planned = listOf(PlannedIncome("srv-p", "Server", 2L, 0)),
                )

            val repository = backupWith(ScriptedSync(Result.success(data)), expenses = untouchable)
            val doc = repository.buildBackup(at).getOrThrow()

            assertEquals(listOf("srv-e"), doc.expenses.map { it.id })
            assertEquals(listOf("srv-i"), doc.income.map { it.id })
            assertEquals(listOf("srv-p"), doc.plannedIncome.map { it.id })
        }

    @Test
    fun nullServerData_fallsBackToTheRepositories() =
        runTest {
            val doc = backupWith(ScriptedSync(Result.success(null))).buildBackup(at).getOrThrow()

            assertEquals(listOf("local-e"), doc.expenses.map { it.id })
            assertEquals(listOf("local-i"), doc.income.map { it.id })
            assertEquals(listOf("local-p"), doc.plannedIncome.map { it.id })
        }

    @Test
    fun anUnreachableServer_failsTheExport_insteadOfFallingBackToCache() =
        runTest {
            val failure = ServerUnreachableException(IllegalStateException("offline"))

            val result = backupWith(ScriptedSync(Result.failure(failure))).buildBackup(at)

            assertTrue(result.exceptionOrNull() is ServerUnreachableException)
        }
}
