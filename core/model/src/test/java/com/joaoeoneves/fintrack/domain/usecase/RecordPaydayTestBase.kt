package com.joaoeoneves.fintrack.domain.usecase

import com.joaoeoneves.fintrack.data.FakeAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.FakeIncomeRepository
import com.joaoeoneves.fintrack.data.FakeMonthSettingsRepository
import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.data.FakeSalaryLineLabelsProvider
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** Shared fixtures for the [RecordPayday] tests: the repositories wired together and a few helpers. */
abstract class RecordPaydayTestBase {
    protected val utc: ZoneOffset = ZoneOffset.UTC
    protected val oct: YearMonth = YearMonth.of(2026, 10)
    protected val incomes = FakeIncomeRepository()
    protected val plan = FakePlannedIncomeRepository()
    protected val monthSettings = FakeMonthSettingsRepository()
    protected val automation = FakeAutomationSettingsRepository(incomeRepo = incomes)

    protected val labels = FakeSalaryLineLabelsProvider()
    protected val useCase = RecordPayday(plan, monthSettings, incomes, automation, labels)

    /** Sets the cursors of the given lines (id to month). */
    protected fun cursors(vararg entries: Pair<String, YearMonth>) {
        automation.replaceSettings(AutomationSettings(lineRecordedThrough = entries.toMap()))
    }

    protected fun line(
        id: String,
        cents: Long = 100_000L,
        managedKey: String? = null,
        autoRecord: Boolean = true,
        payDay: Int? = null,
    ) = PlannedIncome(id, "Line $id", cents, 0, managedKey = managedKey, autoRecord = autoRecord, payDay = payDay)

    protected suspend fun stored(): List<Income> = incomes.getAllIncome().getOrThrow().sortedBy { it.id }

    protected fun at(iso: String): Instant = Instant.parse(iso)

    protected suspend fun cursor(id: String = "p1"): YearMonth? = automation.observeAutomationSettings().first().lineRecordedThrough[id]
}

/** Delegates to [delegate] but fails every [recordPayday] after the first, to test resuming after a partial run. */
class FlakyAfterOne(
    private val delegate: AutomationSettingsRepository,
) : AutomationSettingsRepository by delegate {
    private var calls = 0

    override suspend fun recordPayday(
        incomes: List<Income>,
        cursors: Map<String, YearMonth>,
    ): Result<Unit> {
        val failing = calls++ > 0
        if (failing) return Result.failure(IllegalStateException("offline"))
        return delegate.recordPayday(incomes, cursors)
    }
}
