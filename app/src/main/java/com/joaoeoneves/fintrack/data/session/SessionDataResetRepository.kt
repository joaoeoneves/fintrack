package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.data.FirestoreDataResetRepository
import com.joaoeoneves.fintrack.data.local.LocalDataResetRepository
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesRepository
import com.joaoeoneves.fintrack.domain.model.UserPreferences
import com.joaoeoneves.fintrack.domain.repository.DataResetRepository
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DataResetRepository] that wipes Firestore when signed in and the local store as a guest, then resets the
 * preferences to their defaults (pushed to the account's preferences document when signed in).
 */
@Singleton
class SessionDataResetRepository
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        remote: Lazy<FirestoreDataResetRepository>,
        local: LocalDataResetRepository,
        private val preferences: UserPreferencesRepository,
    ) : DataResetRepository {
        private val switch = SessionSwitch<DataResetRepository>(sessionRepository, remote, local)

        override suspend fun deleteAllData(): Result<Unit> =
            switch
                .call { it.deleteAllData() }
                .mapCatching { resetPreferences() }

        private suspend fun resetPreferences() {
            val defaults = UserPreferences()
            preferences.setThemePreference(defaults.theme)
            preferences.setCurrency(defaults.currency)
            preferences.setLanguage(defaults.language)
        }
    }
