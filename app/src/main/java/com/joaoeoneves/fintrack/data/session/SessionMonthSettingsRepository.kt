package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreMonthSettingsRepository
import com.joaoeoneves.fintrack.data.local.LocalMonthSettingsRepository
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** [MonthSettingsRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionMonthSettingsRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestoreMonthSettingsRepository>,
        local: LocalMonthSettingsRepository,
    ) : MonthSettingsRepository {
        private val switch = SessionSwitch<MonthSettingsRepository>(sessionRepository, remote, local)

        override fun observeMonthSettings(): Flow<List<MonthSettings>> =
            switch.flow(emptyList()) {
                it.observeMonthSettings()
            }

        override suspend fun setWorkingDaysOverride(
            month: YearMonth,
            days: Int?,
        ): Result<Unit> = switch.call { it.setWorkingDaysOverride(month, days) }
    }
