package com.joaoeoneves.fintrack.data.backup

import com.joaoeoneves.fintrack.data.local.LocalDataDocument
import com.joaoeoneves.fintrack.data.local.LocalDataStore
import com.joaoeoneves.fintrack.data.preferences.UserPreferencesStore
import com.joaoeoneves.fintrack.data.runCatchingCancellable
import com.joaoeoneves.fintrack.domain.model.AppLanguage
import com.joaoeoneves.fintrack.domain.model.CurrencyOption
import com.joaoeoneves.fintrack.domain.model.Session
import com.joaoeoneves.fintrack.domain.model.ThemePreference
import com.joaoeoneves.fintrack.domain.repository.SessionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Writes a backup into the guest store and the preferences; refuses unless the session is a guest. */
class GuestBackupRestorer
    @Inject
    constructor(
        private val localDataStore: LocalDataStore,
        private val preferencesStore: UserPreferencesStore,
        private val sessionRepository: SessionRepository,
    ) {
        suspend fun localHasData(): Boolean = !localDataStore.data.first().isEmpty

        suspend fun restore(document: BackupDocument): Result<Unit> =
            runCatchingCancellable {
                check(sessionRepository.session.value == Session.Guest) { "Restore is only available in guest mode" }
                localDataStore
                    .replaceAll(
                        LocalDataDocument(
                            expenses = document.expenses,
                            income = document.income,
                            budgets = document.budgets,
                            categories = document.categories,
                            plannedIncome = document.plannedIncome,
                        ),
                    ).getOrThrow()
                document.preferences?.let { backup ->
                    preferencesStore.update { current ->
                        current.copy(
                            theme = ThemePreference.entries.find { it.name == backup.theme } ?: current.theme,
                            currency = CurrencyOption.entries.find { it.name == backup.currency } ?: current.currency,
                            language = AppLanguage.entries.find { it.name == backup.language } ?: current.language,
                        )
                    }
                }
                Unit
            }
    }
