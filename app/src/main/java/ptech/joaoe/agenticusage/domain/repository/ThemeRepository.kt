package ptech.joaoe.agenticusage.domain.repository

import kotlinx.coroutines.flow.Flow
import ptech.joaoe.agenticusage.domain.model.ThemePreference

interface ThemeRepository {
    fun observeThemePreference(): Flow<ThemePreference>

    suspend fun setThemePreference(preference: ThemePreference)
}
