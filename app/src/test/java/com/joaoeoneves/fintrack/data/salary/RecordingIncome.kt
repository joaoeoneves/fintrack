package com.joaoeoneves.fintrack.data.salary

import com.joaoeoneves.fintrack.data.FakePlannedIncomeRepository
import com.joaoeoneves.fintrack.domain.model.PlannedIncome
import com.joaoeoneves.fintrack.domain.repository.PlannedIncomeRepository

/** Delegates to a [FakePlannedIncomeRepository] and records the ids of the saved lines. */
class RecordingIncome(
    private val delegate: FakePlannedIncomeRepository,
    private val saved: MutableList<String>,
) : PlannedIncomeRepository by delegate {
    override suspend fun savePlannedIncome(line: PlannedIncome): Result<String> {
        saved += line.id
        return delegate.savePlannedIncome(line)
    }
}
