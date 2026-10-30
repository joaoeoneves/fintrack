package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreMonthSettingsRepository
import com.joaoeoneves.fintrack.data.local.LocalMonthSettingsRepository
import com.joaoeoneves.fintrack.di.ApplicationScope
import com.joaoeoneves.fintrack.domain.model.MonthSettings
import com.joaoeoneves.fintrack.domain.repository.MonthSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
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
        @ApplicationScope scope: CoroutineScope,
        remote: Lazy<FirestoreMonthSettingsRepository>,
        local: LocalMonthSettingsRepository,
    ) : MonthSettingsRepository {
        private val switch = SessionSwitch<MonthSettingsRepository>(sessionRepository, remote, local)

        private val observeMonthSettingsShared = switch.shared(scope, emptyList()) { it.observeMonthSettings() }

        override fun observeMonthSettings(): Flow<List<MonthSettings>> = observeMonthSettingsShared

        override suspend fun setDaysOff(
            months: Collection<YearMonth>,
            vacation: Int,
            sick: Int,
            other: Int,
        ): Result<Unit> = switch.call { it.setDaysOff(months, vacation, sick, other) }
    }
