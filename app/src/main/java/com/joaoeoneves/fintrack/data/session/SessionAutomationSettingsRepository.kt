package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreAutomationSettingsRepository
import com.joaoeoneves.fintrack.data.local.LocalAutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.model.AutomationSettings
import com.joaoeoneves.fintrack.domain.model.Income
import com.joaoeoneves.fintrack.domain.repository.AutomationSettingsRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** [AutomationSettingsRepository] that talks to Firestore when signed in and to the local store as a guest. */
@Singleton
class SessionAutomationSettingsRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestoreAutomationSettingsRepository>,
        local: LocalAutomationSettingsRepository,
    ) : AutomationSettingsRepository {
        private val switch = SessionSwitch<AutomationSettingsRepository>(sessionRepository, remote, local)

        override fun observeAutomationSettings(): Flow<AutomationSettings> =
            switch.flow(AutomationSettings()) { it.observeAutomationSettings() }

        override suspend fun setPaydayEnabled(
            enabled: Boolean,
            cursor: YearMonth?,
        ): Result<Unit> = switch.call { it.setPaydayEnabled(enabled, cursor) }

        override suspend fun recordPayday(
            month: YearMonth,
            incomes: List<Income>,
        ): Result<Unit> = switch.call { it.recordPayday(month, incomes) }
    }
